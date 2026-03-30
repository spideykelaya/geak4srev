import org.apache.poi.xwpf.usermodel.*
import java.io.*

object WordService {
  def generateDoc(data: Map[String, String]): Array[Byte] = {
    val templateStream = getClass.getClassLoader.getResourceAsStream("BegehungVorlage.docx")
    println(s"Template stream: $templateStream")
    val doc = new XWPFDocument(templateStream)
    doc.getTables.forEach { table =>
      table.getRows.forEach { row =>
        row.getTableCells.forEach { cell =>
          cell.getParagraphs.forEach { p =>
            if p.getText.contains("$") then
              println(s"Paragraph text: '${p.getText}'")
              p.getRuns.forEach { run =>
                println(s"  Run: '${run.getText(0)}'")
              }
          }
        }
      }
    }
    println(s"Template loaded, paragraphs: ${doc.getParagraphs.size}")

    // Platzhalter in Paragraphen ersetzen
    doc.getParagraphs.forEach { p =>
      p.getRuns.forEach { run =>
        val text = run.getText(0)
        if text != null then
          var replaced = text
          data.foreach { (key, value) =>
            replaced = replaced.replace(s"$${$key}", value)
          }
          if replaced != text then
            println(s"Replaced: $text -> $replaced")
            run.setText(replaced, 0)
      }
    }

    // Platzhalter auch in Tabellen ersetzen
    doc.getTables.forEach { table =>
      table.getRows.forEach { row =>
        row.getTableCells.forEach { cell =>
          cell.getParagraphs.forEach { p =>
            p.getRuns.forEach { run =>
              val text = run.getText(0)
              if text != null then
                var replaced = text
                data.foreach { (key, value) =>
                  replaced = replaced.replace(s"$${$key}", value)
                }
                if replaced != text then
                  println(s"Replaced in table: $text -> $replaced")
                  run.setText(replaced, 0)
            }
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