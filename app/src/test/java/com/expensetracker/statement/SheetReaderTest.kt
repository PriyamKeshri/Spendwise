package com.expensetracker.statement

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SheetReaderTest {

    /** Builds a minimal .xlsx in memory: workbook, relationships, shared strings and one sheet. */
    private fun xlsx(sheet: String, sharedStrings: String): ByteArray {
        val parts = mapOf(
            "[Content_Types].xml" to "<Types/>",
            "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Statement" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to sharedStrings,
            "xl/worksheets/sheet1.xml" to sheet,
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `reads shared, rich, inline and numeric cells and keeps gaps`() {
        val shared = """<sst><si><t>Date</t></si><si><t>Details</t></si><si><t>Amount</t></si>""" +
            """<si><r><t>Paid to </t></r><r><t>Swiggy</t></r><rPh><t>ignored</t></rPh></si></sst>"""
        val sheet = """<worksheet><sheetData>""" +
            """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="D1" t="s"><v>2</v></c></row>""" +
            """<row r="3"><c r="A3"><v>45940.5</v></c><c r="B3" t="s"><v>3</v></c><c r="D3" t="inlineStr"><is><t>-380.00</t></is></c></row>""" +
            """</sheetData></worksheet>"""

        val rows = SheetReader.read(xlsx(sheet, shared))

        assertEquals(listOf("Date", "Details", "", "Amount"), rows[0])
        assertEquals(listOf("45940.5", "Paid to Swiggy", "", "-380.00"), rows[1])
        assertEquals(2, rows.size)
    }

    @Test
    fun `reads csv with quotes, a byte order mark and semicolons`() {
        val csv = "﻿Date;Details;Amount\n10/10/2025;\"Paid to \"\"Chai\"\"; Point\";-20\r\n\n11/10/2025;Received from Rahul;+750\n"

        val rows = SheetReader.read(csv.toByteArray())

        assertEquals(listOf("Date", "Details", "Amount"), rows[0])
        assertEquals(listOf("10/10/2025", "Paid to \"Chai\"; Point", "-20"), rows[1])
        assertEquals(listOf("11/10/2025", "Received from Rahul", "+750"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test(expected = StatementFormatException::class)
    fun `explains that pdf statements can't be read`() {
        SheetReader.read("%PDF-1.7 ...".toByteArray())
    }
}
