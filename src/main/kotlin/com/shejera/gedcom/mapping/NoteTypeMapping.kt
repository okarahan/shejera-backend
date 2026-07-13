package com.shejera.gedcom.mapping

import com.shejera.gedcom.GedcomTags

enum class GedcomNoteExportStyle {
  NOTE,
  FACT_WITH_BIOGRAPHY_TYPE,
}

object NoteTypeMapping {
  val appTypeToExportStyle =
      mapOf(
          "biography" to GedcomNoteExportStyle.NOTE,
          "research" to GedcomNoteExportStyle.NOTE,
          "general" to GedcomNoteExportStyle.NOTE,
      )

  fun gedcomTagFor(style: GedcomNoteExportStyle): String =
      when (style) {
        GedcomNoteExportStyle.NOTE -> GedcomTags.NOTE
        GedcomNoteExportStyle.FACT_WITH_BIOGRAPHY_TYPE -> GedcomTags.FACT
      }

  const val BIOGRAPHY_FACT_TYPE = "Biography"
}
