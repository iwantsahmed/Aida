package com.rayluxeo.aida.ui.scanner

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the Files screen puts in front of Abu, in one language: the title, the source-filter
 * chips, the empty state, the detail sheet with its rename, delete and link actions, the undo
 * notice and the handoff that asks Mira about a document.
 *
 * English and Bengali are separate implementations so either can be reworded without reading the
 * other, exactly as the shell, tasks and notes phrasebooks are. The notices Mira shows after a
 * rename or a delete are worded here too, so the view model never types a sentence in one
 * language — it asks for the one that matches Abu's setting.
 */
internal interface FilesWording {

    val screenTitle: String

    /** What TalkBack announces for the back arrow. It is never drawn, only spoken. */
    val back: String

    /** The control that waves away the undo notice above the list. */
    val dismiss: String

    /** Puts back the file the last delete moved to the bin. */
    val undo: String

    val searchPlaceholder: String

    /** The chip that clears the source filter and shows every file. */
    val allFilter: String

    /** A source shown the way Abu reads it, not the raw enum constant. */
    fun sourceLabel(source: FileSource): String

    val openScanner: String

    /** What an empty list says, with the way forward named in the same breath. */
    val emptyFiles: String

    /** A file's size, worded in bytes, KB or MB in the language's own digits. */
    fun fileSize(bytes: Long): String

    /**
     * The one-line summary at the top of the detail sheet. The MIME type is a technical token and
     * passes through untranslated; only the labels around it change with language.
     */
    fun fileDetails(source: FileSource, bytes: Long, mimeType: String): String

    /** The heading over a scanned document's recognised text. */
    val extractedTextLabel: String

    val rename: String

    val delete: String

    val linkClient: String

    val linkProject: String

    val askMiraAboutDocument: String

    /** The words handed to Mira when Abu asks her about a document, with the file's own name in them. */
    fun askMiraPrompt(name: String): String

    // --- The rename dialog ---------------------------------------------------------------------

    val renameTitle: String

    val fileNameLabel: String

    val save: String

    val cancel: String

    // --- The delete dialog ---------------------------------------------------------------------

    val deleteTitle: String

    /** The question's detail, with the file's own name quoted back. */
    fun deleteMessage(name: String): String

    // --- Linking to a client or a project ------------------------------------------------------

    val linkToClient: String

    val linkToProject: String

    /** Severs an existing link rather than choosing a new one. */
    val unlink: String

    /** What the link picker shows when there is nothing yet to link to. */
    val nothingToLink: String

    /** The marker beside the client or project a file is already linked to. */
    val linked: String

    // --- What Mira says afterwards -------------------------------------------------------------

    val renamed: String

    val fileMovedToBin: String
}

internal fun filesWording(language: SpokenLanguage): FilesWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliFilesWording
    SpokenLanguage.ENGLISH -> EnglishFilesWording
}

/** The Files screen in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishFilesWording : FilesWording {

    override val screenTitle: String = "Files & Documents"

    override val back: String = "Back"

    override val dismiss: String = "Dismiss"

    override val undo: String = "Undo"

    override val searchPlaceholder: String = "Search by name or text inside"

    override val allFilter: String = "All"

    override fun sourceLabel(source: FileSource): String = when (source) {
        FileSource.SCAN -> "Scans"
        FileSource.IMPORT -> "Imports"
        FileSource.RECORDING -> "Recordings"
        FileSource.EXPORT -> "Exports"
        FileSource.GENERATED -> "Generated"
    }

    override val openScanner: String = "Open scanner"

    override val emptyFiles: String =
        "No files here yet. Scan a document or import one to keep it findable."

    override fun fileSize(bytes: Long): String = when {
        bytes < KILOBYTE -> "$bytes B"
        bytes < MEGABYTE -> "${bytes / KILOBYTE} KB"
        else -> "${bytes / MEGABYTE} MB"
    }

    override fun fileDetails(source: FileSource, bytes: Long, mimeType: String): String =
        "Source: ${sourceLabel(source)} · Size: ${fileSize(bytes)} · $mimeType"

    override val extractedTextLabel: String = "Extracted text"

    override val rename: String = "Rename"

    override val delete: String = "Delete"

    override val linkClient: String = "Link client"

    override val linkProject: String = "Link project"

    override val askMiraAboutDocument: String = "Ask Aida about document"

    override fun askMiraPrompt(name: String): String = "Analyze this document: $name"

    override val renameTitle: String = "Rename file"

    override val fileNameLabel: String = "File name"

    override val save: String = "Save"

    override val cancel: String = "Cancel"

    override val deleteTitle: String = "Delete this file?"

    override fun deleteMessage(name: String): String =
        "\"$name\" moves to the bin. You can undo straight after."

    override val linkToClient: String = "Link to client"

    override val linkToProject: String = "Link to project"

    override val unlink: String = "Unlink"

    override val nothingToLink: String = "Nothing to link to yet."

    override val linked: String = "Linked"

    override val renamed: String = "Renamed."

    override val fileMovedToBin: String = "File moved to bin."
}

/**
 * The Files screen in Bengali. Bengali script throughout — a screen that drops an English chip into
 * a Bengali list is harder to read than either language on its own.
 *
 * Numbers go through [bengaliNumerals], as the tasks and notes wording do: Latin digits inside a
 * Bengali sentence are the one place a bilingual screen always slips. A file's MIME type is a
 * technical token and stays in Latin, because naming it is the point.
 */
internal object BengaliFilesWording : FilesWording {

    override val screenTitle: String = "ফাইল ও নথি"

    override val back: String = "ফিরে যান"

    override val dismiss: String = "বন্ধ করুন"

    override val undo: String = "ফিরিয়ে আনুন"

    override val searchPlaceholder: String = "নাম বা ভেতরের লেখা দিয়ে খুঁজুন"

    override val allFilter: String = "সব"

    override fun sourceLabel(source: FileSource): String = when (source) {
        FileSource.SCAN -> "স্ক্যান"
        FileSource.IMPORT -> "আমদানি"
        FileSource.RECORDING -> "রেকর্ডিং"
        FileSource.EXPORT -> "রপ্তানি"
        FileSource.GENERATED -> "তৈরি করা"
    }

    override val openScanner: String = "স্ক্যানার খুলুন"

    override val emptyFiles: String =
        "এখনো কোনো ফাইল নেই। একটি নথি স্ক্যান করুন বা আমদানি করুন, যাতে পরে খুঁজে পাওয়া যায়।"

    override fun fileSize(bytes: Long): String = when {
        bytes < KILOBYTE -> "${digits(bytes)} বাইট"
        bytes < MEGABYTE -> "${digits(bytes / KILOBYTE)} কেবি"
        else -> "${digits(bytes / MEGABYTE)} এমবি"
    }

    override fun fileDetails(source: FileSource, bytes: Long, mimeType: String): String =
        "উৎস: ${sourceLabel(source)} · আকার: ${fileSize(bytes)} · $mimeType"

    override val extractedTextLabel: String = "উদ্ধার করা লেখা"

    override val rename: String = "নাম পরিবর্তন"

    override val delete: String = "মুছে ফেলুন"

    override val linkClient: String = "ক্লায়েন্ট যুক্ত করুন"

    override val linkProject: String = "প্রকল্প যুক্ত করুন"

    override val askMiraAboutDocument: String = "এই নথি নিয়ে আইদাকে জিজ্ঞাসা করুন"

    override fun askMiraPrompt(name: String): String = "এই নথিটি বিশ্লেষণ করুন: $name"

    override val renameTitle: String = "ফাইলের নাম পরিবর্তন"

    override val fileNameLabel: String = "ফাইলের নাম"

    override val save: String = "সংরক্ষণ করুন"

    override val cancel: String = "বাতিল"

    override val deleteTitle: String = "এই ফাইলটি মুছবেন?"

    override fun deleteMessage(name: String): String =
        "\"$name\" ঝুড়িতে চলে যাবে। ঠিক পরেই ফিরিয়ে আনতে পারবেন।"

    override val linkToClient: String = "ক্লায়েন্টের সঙ্গে যুক্ত করুন"

    override val linkToProject: String = "প্রকল্পের সঙ্গে যুক্ত করুন"

    override val unlink: String = "সংযোগ সরান"

    override val nothingToLink: String = "যুক্ত করার মতো এখনো কিছু নেই।"

    override val linked: String = "যুক্ত"

    override val renamed: String = "নাম পরিবর্তন হয়েছে।"

    override val fileMovedToBin: String = "ফাইল ঝুড়িতে সরানো হয়েছে।"

    private fun digits(value: Long): String = bengaliNumerals(value.toString())
}

/** Bytes to a kilobyte and to a megabyte, shared by both languages so the thresholds never drift. */
private const val KILOBYTE = 1_024L

private const val MEGABYTE = 1_024L * 1_024L
