import org.apache.poi.xwpf.usermodel.*
import java.io.*

object WordService {
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

  private def replacePlaceholdersInParagraph(p: XWPFParagraph, data: Map[String, String]): Unit = {
    // Get the full text of the paragraph
    var fullText = p.getText
    println(s"Paragraph text before: '$fullText'")

    // Replace all placeholders
    data.foreach { (key, value) =>
      fullText = fullText.replace(s"$${$key}", value)
    }

    if fullText != p.getText then
      println(s"Paragraph text after: '$fullText'")
      // Clear all runs and add new single run with replaced text
      p.getRuns.size match {
        case 0 =>
          // No runs, create one
          val run = p.createRun()
          run.setText(fullText)
        case _ =>
          // Replace content: clear all runs and use the first one
          while p.getRuns.size > 1 do
            p.removeRun(1)

          val firstRun = p.getRuns.get(0)
          firstRun.setText(fullText, 0)
      }
    end if
  }
}