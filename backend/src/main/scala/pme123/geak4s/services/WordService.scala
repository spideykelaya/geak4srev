import org.apache.poi.xwpf.usermodel.*
import java.io.*

object WordService {

  private def replacePlaceholdersInParagraph(p: XWPFParagraph, data: Map[String, String]): Unit =
    val runs = p.getRuns
    if runs.isEmpty then return

    data.foreach { (key, value) =>
      val placeholder = s"$${$key}"
      val runTexts = (0 until runs.size).map(i => Option(runs.get(i).getText(0)).getOrElse(""))
      val fullText = runTexts.mkString
      val idx = fullText.indexOf(placeholder)

      if idx >= 0 then
        val placeholderEnd = idx + placeholder.length
        // Kumulative Positionen: cumPos(i) = Startposition von Run i im Gesamttext
        val cumPos = runTexts.scanLeft(0)(_ + _.length)

        // Run der den Platzhalter-Anfang ('$') enthält
        val startRunIdx = (0 until runs.size).find(i => cumPos(i) <= idx && cumPos(i + 1) > idx).getOrElse(0)
        // Run der das Platzhalter-Ende ('}') enthält
        val endRunIdx = (0 until runs.size).find(i => cumPos(i) < placeholderEnd && cumPos(i + 1) >= placeholderEnd).getOrElse(startRunIdx)

        val beforeInStart = runTexts(startRunIdx).substring(0, idx - cumPos(startRunIdx))
        val afterInEnd = runTexts(endRunIdx).substring(placeholderEnd - cumPos(endRunIdx))

        // Start-Run: Text vor Platzhalter + Ersatzwert (behält die Formatierung des Start-Runs)
        runs.get(startRunIdx).setText(beforeInStart + value + (if startRunIdx == endRunIdx then afterInEnd else ""), 0)

        if startRunIdx != endRunIdx then
          // Runs zwischen Start und Ende leeren
          (startRunIdx + 1 until endRunIdx).foreach(i => runs.get(i).setText("", 0))
          // End-Run: nur Text nach dem Platzhalter (behält seine ursprüngliche Formatierung)
          runs.get(endRunIdx).setText(afterInEnd, 0)

        println(s"Replaced '$placeholder' -> '$value' (runs $startRunIdx-$endRunIdx)")
    }

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
