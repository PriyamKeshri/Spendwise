package com.expensetracker.statement

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.TreeMap
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

class StatementFormatException(message: String) : Exception(message)

/**
 * Turns a spreadsheet into rows of cell text. Reads .xlsx (a zip of XML parts, so no spreadsheet
 * library is needed) and comma, semicolon or tab separated text. Numeric cells keep their raw value,
 * so a date stored as an Excel serial number comes through as e.g. "45941.6".
 */
object SheetReader {

    private const val MAX_PART_BYTES = 64 * 1024 * 1024

    fun read(bytes: ByteArray): List<List<String>> = when {
        bytes.startsWith("PK") -> readXlsx(bytes)
        bytes.size >= 2 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() ->
            throw StatementFormatException("Old .xls files aren't supported. Download the .xlsx version.")
        bytes.startsWith("%PDF") ->
            throw StatementFormatException("PDFs aren't supported. Download the Excel version.")
        else -> readDelimited(bytes.decodeToString().removePrefix("﻿"))
    }

    private fun ByteArray.startsWith(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    // region xlsx

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                if (name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" || name.startsWith("xl/worksheets/")
                ) {
                    parts[name] = zip.readUpTo(MAX_PART_BYTES)
                }
            }
        }
        val shared = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheet = firstSheetPath(parts)?.let { parts[it] }
            ?: parts.entries.filter { it.key.startsWith("xl/worksheets/sheet") }.minByOrNull { it.key }?.value
            ?: throw StatementFormatException("This file has no data.")
        return parseSheet(sheet, shared)
    }

    /** The workbook lists sheets in tab order; its relationships file says which XML part each one is. */
    private fun firstSheetPath(parts: Map<String, ByteArray>): String? {
        val workbook = parts["xl/workbook.xml"] ?: return null
        val rels = parts["xl/_rels/workbook.xml.rels"] ?: return null
        var firstSheetId: String? = null
        val targets = HashMap<String, String>()
        parseElements(workbook) { name, attributes ->
            if (name == "sheet" && firstSheetId == null) firstSheetId = attributes.value("id")
        }
        parseElements(rels) { name, attributes ->
            val id = attributes.value("Id")
            val target = attributes.value("Target")
            if (name == "Relationship" && id != null && target != null) targets[id] = target
        }
        val target = firstSheetId?.let { targets[it] } ?: return null
        return if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = ArrayList<String>()
        val current = StringBuilder()
        var inText = false
        var phonetic = 0
        newParser().parse(
            ByteArrayInputStream(xml),
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    when (qName.local()) {
                        "si" -> current.setLength(0)
                        "t" -> inText = true
                        "rPh" -> phonetic++
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName.local()) {
                        "si" -> strings += current.toString()
                        "t" -> inText = false
                        "rPh" -> phonetic--
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inText && phonetic == 0) current.append(ch, start, length)
                }
            },
        )
        return strings
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val rows = TreeMap<Int, MutableMap<Int, String>>()
        var rowIndex = -1
        var nextColumn = 0
        var column = 0
        var type: String? = null
        val value = StringBuilder()
        var capturing = false
        newParser().parse(
            ByteArrayInputStream(xml),
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    when (qName.local()) {
                        "row" -> {
                            rowIndex = attributes.value("r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                            nextColumn = 0
                        }
                        "c" -> {
                            column = attributes.value("r")?.let(::columnIndex) ?: nextColumn
                            type = attributes.value("t")
                            value.setLength(0)
                        }
                        "v", "t" -> capturing = true
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName.local()) {
                        "v", "t" -> capturing = false
                        "c" -> {
                            val raw = value.toString()
                            val text = when (type) {
                                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                else -> raw
                            }.trim()
                            if (text.isNotEmpty() && rows.size < MAX_ROWS) rows.getOrPut(rowIndex) { HashMap() }[column] = text
                            nextColumn = column + 1
                        }
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (capturing) value.append(ch, start, length)
                }
            },
        )
        return rows.values.map { cells ->
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            List(width) { cells[it].orEmpty() }
        }
    }

    /** "AB12" -> 27 (zero-based column of the cell reference). */
    private fun columnIndex(reference: String): Int? {
        var column = 0
        var letters = 0
        for (ch in reference) {
            if (!ch.isLetter()) break
            column = column * 26 + (ch.uppercaseChar() - 'A' + 1)
            letters++
        }
        return if (letters == 0) null else column - 1
    }

    // endregion

    // region delimited text

    private fun readDelimited(text: String): List<List<String>> {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(10).toList()
        if (sample.isEmpty()) return emptyList()
        val delimiter = listOf(',', ';', '\t').maxBy { d -> sample.sumOf { line -> line.count { it == d } } }

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        fun endCell() {
            row += cell.toString().trim()
            cell.setLength(0)
        }
        fun endRow() {
            endCell()
            if (row.any { it.isNotEmpty() } && rows.size < MAX_ROWS) rows += row
            row = ArrayList()
        }

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delimiter -> endCell()
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // endregion

    private const val MAX_ROWS = 50_000

    private fun String.local(): String = substringAfter(':')

    private fun Attributes.value(name: String): String? {
        for (i in 0 until length) {
            if (getQName(i).local() == name) return getValue(i)
        }
        return null
    }

    private fun parseElements(xml: ByteArray, onStart: (name: String, attributes: Attributes) -> Unit) {
        newParser().parse(
            ByteArrayInputStream(xml),
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    onStart(qName.local(), attributes)
                }
            },
        )
    }

    /** Spreadsheets come from outside the app, so external entities and DTDs are switched off. */
    private fun newParser(): SAXParser = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newSAXParser()

}

/** Reads the whole stream, refusing anything over [limit] bytes (a zip can expand enormously). */
internal fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw StatementFormatException("This file is too big to import.")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
