package pme123.geak4s.domain.project

import upickle.default.{ReadWriter, macroRW}

/** All form fields of the Begehungsprotokoll (Step 4).
 *  Persisted as part of GeakProject so nothing is lost on JSON export/import.
 */
case class WordFormData(
  projektnummer: String = "",
  auftraggeberin: String = "",
  mail: String = "",
  tel: String = "",
  adresse: String = "",
  baujahr: String = "",
  datum: String = "",
  egid: String = "",
  heizung: String = "",
  warmwasser: String = "",
  gebaudeart: String = "",
  ebf: String = "",
  wohnungen: String = "",
  energieart: String = "",
  energieverbrauch: String = "",
  energiekennzahl: String = "",
  erdsonde: String = "",
  fernwärme: String = "",
  fossil: String = "",
  wp: String = "",
  sondentiefe: String = ""
)

object WordFormData:
  given ReadWriter[WordFormData] = macroRW
