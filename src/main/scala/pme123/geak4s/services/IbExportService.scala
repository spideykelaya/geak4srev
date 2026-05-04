package pme123.geak4s.services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import org.scalajs.dom
import pme123.geak4s.domain.project.WordFormData

object IbExportService:

  @js.native
  @JSImport("pdf-lib", "PDFDocument")
  object PDFDocument extends js.Object:
    def load(data: js.Any): js.Promise[js.Dynamic] = js.native

  private val EfhUrl  = "templates/IB_EFH_v3.6_.pdf"
  private val GmfhUrl = "templates/IB_gMFH_v2.5_.pdf"

  def generateEfh(f: WordFormData): Future[dom.Blob] =
    fetchAndFill(EfhUrl, fillEfh(_, f))

  def generateGmfh(f: WordFormData): Future[dom.Blob] =
    fetchAndFill(GmfhUrl, fillGmfh(_, f))

  private def fetchAndFill(url: String, fill: js.Dynamic => Unit): Future[dom.Blob] =
    dom.fetch(url).toFuture
      .flatMap { resp =>
        if !resp.ok then throw new RuntimeException(s"PDF konnte nicht geladen werden: ${resp.status}")
        resp.arrayBuffer().toFuture
      }
      .flatMap { buffer =>
        PDFDocument.load(buffer.asInstanceOf[js.Any]).toFuture.map { pdfDoc =>
          fill(pdfDoc)
          pdfDoc
        }
      }
      .flatMap { pdfDoc =>
        pdfDoc.save().asInstanceOf[js.Promise[js.typedarray.Uint8Array]].toFuture.map { bytes =>
          new dom.Blob(
            js.Array(bytes.buffer),
            new dom.BlobPropertyBag { `type` = "application/pdf" }
          )
        }
      }

  private def setText(form: js.Dynamic, fieldName: String, value: String, fontSize: Int = 10): Unit =
    try
      if value.nonEmpty then
        val field = form.getTextField(fieldName)
        field.setFontSize(fontSize)
        field.setText(value)
    catch case _: Exception => () // Feld existiert nicht oder falscher Typ → überspringen

  private def splitName(fullName: String): (String, String) =
    val parts = fullName.trim.split(" ")
    if parts.length > 1 then (parts.dropRight(1).mkString(" "), parts.last)
    else ("", fullName)

  private def splitAdresse(adresse: String): (String, String, String) =
    val parts   = adresse.split(",", 2)
    val strasse = parts.headOption.map(_.trim).getOrElse("")
    val rest    = parts.lift(1).map(_.trim).getOrElse("")
    val tokens  = rest.split(" ", 2).filter(_.nonEmpty)
    val plz     = tokens.headOption.filter(_.forall(_.isDigit)).getOrElse("")
    val ort     = tokens.lift(1).getOrElse(if plz.isEmpty then rest else "")
    (strasse, plz, ort)

  private def fillCommon(form: js.Dynamic, f: WordFormData): Unit =
    val (strasse, plz, ort) = splitAdresse(f.adresse)
    val (vorname, name)     = splitName(f.auftraggeberin)
    val ortDatum            = if ort.nonEmpty && f.datum.nonEmpty then s"$ort, ${f.datum}"
                              else if ort.nonEmpty then ort
                              else f.datum
    setText(form, "K_Vorname",  vorname)
    setText(form, "K_Name",     name)
    setText(form, "K_Adresse",  f.ibEigentuemerAdresse)
    setText(form, "K_PLZ",      f.ibEigentuemerPlz)
    setText(form, "K_Ort",      f.ibEigentuemerOrt)
    setText(form, "K_Telefon",  f.tel)
    setText(form, "K_E-Mail",   f.mail)
    setText(form, "O_Adresse",  strasse)
    setText(form, "O_PLZ",      plz)
    setText(form, "O_Ort",      ort)
    setText(form, "EGID",       f.egid)
    setText(form, "EGID_2",     f.egid)
    setText(form, "Ort_Datum_1", ortDatum)
    setText(form, "Ort_Datum_2", ortDatum)
    setText(form, "Energiebezugsflaeche", f.ebf)
    setText(form, "Energiekennzahl",      f.energiekennzahl)
    setText(form, "Verbrauch",            f.energieverbrauch)

  private def fillEfh(pdfDoc: js.Dynamic, f: WordFormData): Unit =
    val form = pdfDoc.getForm().asInstanceOf[js.Dynamic]
    fillCommon(form, f)
    val (vorname, name) = splitName(f.auftraggeberin)
    setText(form, "Datum",           f.datum)
    setText(form, "Wohneinheiten",   f.wohnungen)
    setText(form, "O_Baujahr",       f.baujahr)
    setText(form, "H_Baujahr",            f.ibBaujahrHeizung)
    setText(form, "Gebaeudeerneuerungen", f.ibGebaeudeErneuerungen)
    setText(form, "Hz1", f.energieverbrauch)
    setText(form, "Hz2", f.energieverbrauch)
    setText(form, "Hz3", f.energieverbrauch)
    // Abschlussseite (Felder wiederholen sich am Ende des Dokuments)
    setText(form, "K_Vorname_2",  vorname)
    setText(form, "K_Name_2",     name)
    val (strasse, plz, ort) = splitAdresse(f.adresse)
    setText(form, "O_Adresse_2", strasse)
    setText(form, "O_PLZ_2",     plz)
    setText(form, "O_Ort_2",     ort)

  private def fillGmfh(pdfDoc: js.Dynamic, f: WordFormData): Unit =
    val form = pdfDoc.getForm().asInstanceOf[js.Dynamic]
    fillCommon(form, f)
    val (vorname, name) = splitName(f.auftraggeberin)
    // Kanton aus Ort extrahieren (z.B. "Rüti ZH" → "ZH")
    val (_, _, ort) = splitAdresse(f.adresse)
    val ortParts    = ort.trim.split(" ")
    val kanton      = if ortParts.length > 1 && ortParts.last.matches("[A-Z]{2}") then ortParts.last else ""
    setText(form, "Datum_af_date",          f.datum)
    setText(form, "O_Kanton",              kanton)
    setText(form, "O_Anzahl Wohneinheiten", f.wohnungen)
    setText(form, "O_Anzahl Gebaeude",      f.ibAnzahlGebaeude)
    setText(form, "O_Anzahl Heizraeume",    f.ibAnzahlHeizungsraeume)
    setText(form, "Baujahr_1",              f.baujahr)
    setText(form, "Baujahr_2",              f.ibBaujahrHeizung)
    setText(form, "Hz_1", f.energieverbrauch)
    setText(form, "Hz_2", f.energieverbrauch)
    setText(form, "Hz_3", f.energieverbrauch)
    // Ansprechperson Eigentümerschaft
    setText(form, "E_Vorname",  f.ibAnsprechVorname)
    setText(form, "E_Name",     f.ibAnsprechName)
    setText(form, "E_Adresse",  f.ibAnsprechAdresse)
    setText(form, "E_PLZ",      f.ibAnsprechPlz)
    setText(form, "E_Ort",      f.ibAnsprechOrt)
    setText(form, "E_Telefon",  f.ibAnsprechTel)
    setText(form, "E_E-Mail",   f.ibAnsprechMail)
    // Abschlussseite
    setText(form, "E_Vorname_2", f.ibAnsprechVorname)
    setText(form, "E_Name_2",    f.ibAnsprechName)

end IbExportService
