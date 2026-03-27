import cask.MainRoutes
import org.apache.poi.xwpf.usermodel._
import java.io._

object Main extends MainRoutes {

  @cask.post("/generate")
  def generate() = {

    val doc = new XWPFDocument()

    val paragraph = doc.createParagraph()
    val run = paragraph.createRun()
    run.setText("Hallo! Dein Dokument wurde erstellt 🎉")

    val file = new File("output.docx")
    val out = new FileOutputStream(file)

    doc.write(out)
    out.close()
    doc.close()

    cask.Response("OK")
  }

  initialize()
}