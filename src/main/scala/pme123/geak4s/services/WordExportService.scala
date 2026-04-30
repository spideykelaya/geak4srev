package pme123.geak4s.services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.ArrayBuffer
import org.scalajs.dom
import pme123.geak4s.domain.project.WordFormData

/** Generates the Begehungsprotokoll DOCX from a [[WordFormData]] form on the client.
 *
 *  Loads `public/templates/BegehungVorlage.docx`, fills `${...}` placeholders
 *  via docxtemplater, and returns the resulting `.docx` as a [[dom.Blob]].
 *
 *  Custom delimiters `${` `}` are configured to keep the existing template
 *  unchanged — most placeholders are split across XML runs, which docxtemplater
 *  handles natively.
 *
 *  Note: pizzip and docxtemplater are imported via local default-export
 *  facades because the auto-generated ScalablyTyped facade for pizzip uses a
 *  namespace import that is not callable under Vite's ESM + CJS interop.
 */
object WordExportService:

  @js.native
  @JSImport("pizzip", JSImport.Default)
  private class PizZip(data: js.Any) extends js.Object

  @js.native
  @JSImport("docxtemplater", JSImport.Default)
  private class Docxtemplater(zip: js.Any, options: js.Object) extends js.Object:
    def render(data: js.Object): this.type = js.native
    def getZip(): js.Dynamic                = js.native

  private val TemplateUrl = "templates/BegehungVorlage.docx"

  def generate(formData: WordFormData): Future[dom.Blob] =
    fetchTemplate().map { buffer =>
      val zip  = new PizZip(buffer.asInstanceOf[js.Any])
      val opts = js.Dynamic.literal(
        delimiters    = js.Dynamic.literal(start = "${", end = "}"),
        paragraphLoop = true,
        linebreaks    = true
      ).asInstanceOf[js.Object]
      val doc = new Docxtemplater(zip.asInstanceOf[js.Any], opts)
      doc.render(toData(formData))
      val out = doc.getZip().generate(
        js.Dynamic.literal(
          `type`     = "blob",
          mimeType   =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          compression = "DEFLATE"
        )
      )
      out.asInstanceOf[dom.Blob]
    }

  private def fetchTemplate(): Future[ArrayBuffer] =
    dom.fetch(TemplateUrl).toFuture.flatMap { resp =>
      if !resp.ok then
        throw new RuntimeException(s"Vorlage konnte nicht geladen werden: ${resp.status}")
      resp.arrayBuffer().toFuture
    }

  private def toData(f: WordFormData): js.Object =
    js.Dynamic.literal(
      projektnummer    = f.projektnummer,
      auftraggeberin   = f.auftraggeberin,
      mail             = f.mail,
      tel              = f.tel,
      adresse          = f.adresse,
      baujahr          = f.baujahr,
      datum            = f.datum,
      egid             = f.egid,
      heizung          = f.heizung,
      warmwasser       = f.warmwasser,
      gebaudeart       = f.gebaudeart,
      ebf              = f.ebf,
      wohnungen        = f.wohnungen,
      energieart       = f.energieart,
      energieverbrauch = f.energieverbrauch,
      energiekennzahl  = f.energiekennzahl,
      erdsonde         = f.erdsonde,
      fernwärme        = f.fernwärme,
      fossil           = f.fossil,
      wp               = f.wp,
      sondentiefe      = f.sondentiefe
    )
end WordExportService
