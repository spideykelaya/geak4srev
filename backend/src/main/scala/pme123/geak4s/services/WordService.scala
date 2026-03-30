import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.{ByteArrayOutputStream, File, FileInputStream}

object WordService {

  // Generiert Word-Dokument anhand Vorlage
  def generateDoc(projectId: String): Array[Byte] = {
    val templateFile = new File("backend/src/main/resources/template.docx")
    val doc = new XWPFDocument(new FileInputStream(templateFile))

    // Platzhalter ersetzen
    doc.getParagraphs.forEach { p =>
      val text = p.getText
      p.getRuns.forEach { run =>
        run.setText(run.getText(0)
          .replace("{PROJECT_NAME}", s"Projekt-$projectId")
          .replace("{OWNER}", "Max Mustermann"), 0)
      }
    }

    val baos = new ByteArrayOutputStream()
    doc.write(baos)
    doc.close()
    baos.toByteArray
  }
}