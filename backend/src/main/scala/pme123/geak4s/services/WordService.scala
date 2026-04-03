import org.apache.poi.xwpf.usermodel.*
import java.io.*

object WordService {

  private def replacePlaceholdersInParagraph(p: XWPFParagraph, data: Map[String, String]): Unit =
    val fullText = p.getText
    if fullText != null && fullText.contains("$") then
      var replaced = fullText
      data.foreach { (key, value) =>
        replaced = replaced.replace(s"$${$key}", value)
      }
      if replaced != fullText then
        println(s"Replaced: '$fullText' -> '$replaced'")
        val runs = p.getRuns
        if !runs.isEmpty then
          runs.get(0).setText(replaced, 0)
          (1 until runs.size).foreach(i => runs.get(i).setText("", 0))

  def generateDoc(data: Map[String, String]): Array[Byte] = {
    val templateStream = getClass.getClassLoader.getResourceAsStream("BegehungVorlage.docx")
    println(s"Template stream: $templateStream")
    val doc = new XWPFDocument(templateStream)
    println(s"Template loaded, paragraphs: ${doc.getParagraphs.size}")

    // Platzhalter in Paragraphen ersetzen
    doc.getParagraphs.forEach { p =>
      replacePlaceholdersInParagraph(p, data)
    }

    // Platzhalter auch in Tabellen ersetzen
    doc.getTables.forEach { table =>
      table.getRows.forEach { row =>
        row.getTableCells.forEach { cell =>
          cell.getParagraphs.forEach { p =>
            replacePlaceholdersInParagraph(p, data)
          }
        }
      }
    }

    val baos = new ByteArrayOutputStream()
    doc.write(baos)
    doc.close()
    baos.toByteArray
  }
}