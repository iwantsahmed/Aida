package com.rayluxeo.aida.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.rayluxeo.aida.R

/**
 * Mira's icon set — the Solar family (CC0), bundled as vector drawables and loaded as
 * [ImageVector] so every existing `icon: ImageVector` call site keeps working. Linear is the
 * resting weight; [Filled] carries the Bold cut for a selected tab or an active toggle.
 *
 * Each accessor reads a bundled drawable whose colour is baked white, so an `Icon(tint = …)`
 * recolours it to Mira's ivory or champagne exactly as a Material icon would. Generated to
 * match the Material names it replaced, so the swap at call sites is one-for-one.
 */
object MiraIcons {

    val AccountBalanceWallet: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_account_balance_wallet)

    val Add: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_add)

    val Alarm: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_alarm)

    val ArrowBack: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_back)

    val ArrowDownward: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_downward)

    val ArrowUpward: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_upward)

    val Article: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_article)

    val Assistant: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_assistant)

    val AutoAwesome: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_auto_awesome)

    val AutoAwesomeMotion: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_auto_awesome_motion)

    val Autorenew: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_autorenew)

    val Bedtime: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_bedtime)

    val Block: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_block)

    val Bookmark: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_bookmark)

    val Cake: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_cake)

    val CalendarMonth: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_calendar_month)

    val CalendarToday: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_calendar_today)

    val Call: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_call)

    val CameraAlt: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_camera_alt)

    /** The three bars: business reports. */
    val Chart: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_chart)

    val Check: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_check)

    val CheckBox: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_check_box)

    val CheckBoxOutlineBlank: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_check_box_outline_blank)

    val CheckCircle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_check_circle)

    val Close: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_close)

    val Cloud: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_cloud)

    val ContentCopy: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_content_copy)

    val Delete: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete)

    val DeleteOutline: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete_outline)

    val DeleteSweep: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete_sweep)

    val Description: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_description)

    val DocumentScanner: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_document_scanner)

    val DoneAll: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_done_all)

    val Download: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_download)

    val Edit: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_edit)

    val ErrorOutline: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_error_outline)

    val Event: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_event)

    val FileDownload: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_file_download)

    val FileUpload: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_file_upload)

    val FilterBAndW: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_filter_b_and_w)

    val FilterList: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_filter_list)

    val Fingerprint: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_fingerprint)

    val Forum: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_forum)

    val GraphicEq: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_graphic_eq)

    val Groups: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_groups)

    val History: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_history)

    val HistoryEdu: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_history_edu)

    val Home: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_home)

    val HourglassEmpty: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_hourglass_empty)

    val Image: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_image)

    val Inbox: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_inbox)

    val InsertDriveFile: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_insert_drive_file)

    val Inventory2: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_inventory2)

    val Key: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_key)

    val KeyboardArrowRight: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_keyboard_arrow_right)

    val KeyboardVoice: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_keyboard_voice)

    val Link: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_link)

    val ListAlt: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_list_alt)

    val Lock: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_lock)

    val Message: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_message)

    val Mic: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_mic)

    val MoreHoriz: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_more_horiz)

    val MoreVert: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_more_vert)

    val Notes: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_notes)

    val NotificationsActive: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_notifications_active)

    val NotificationsNone: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_notifications_none)

    val Paid: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_paid)

    val Pause: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_pause)

    val Person: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_person)

    val PersonAdd: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_person_add)

    val PersonPin: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_person_pin)

    val PictureAsPdf: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_picture_as_pdf)

    val Place: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_place)

    val PlayArrow: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_play_arrow)

    val PriorityHigh: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_priority_high)

    val Psychology: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_psychology)

    val PushPin: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_push_pin)

    val RadioButtonChecked: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_radio_button_checked)

    val RadioButtonUnchecked: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_radio_button_unchecked)

    val ReceiptLong: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_receipt_long)

    val RecordVoiceOver: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_record_voice_over)

    val Redo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_redo)

    val Refresh: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_refresh)

    val Remove: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_remove)

    val Repeat: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_repeat)

    val Salah: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_salah)

    val Save: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_save)

    val Schedule: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_schedule)

    val Search: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_search)

    val Send: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_send)

    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_settings)

    val Shield: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_shield)

    val Stop: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_stop)

    val Subject: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_subject)

    val SwapVert: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_swap_vert)

    val TaskAlt: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_task_alt)

    val Timer: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_timer)

    val TouchApp: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_touch_app)

    val TripOrigin: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_trip_origin)

    val Unarchive: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_unarchive)

    val Undo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_undo)

    val Vibration: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_vibration)

    val WbSunny: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_wb_sunny)

    /** The 2×2 grid: the Areas hub, where every feature outside the bottom bar lives. */
    val Widget: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_widget)

    val WorkOutline: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_work_outline)

    /** The Bold weight, for selected tabs and active/toggled states. */
    object Filled {

        val Bookmark: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_bookmark_bold)

        val CheckBox: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_check_box_bold)

        val CheckCircle: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_check_circle_bold)

        val GraphicEq: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_graphic_eq_bold)

        val Home: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_home_bold)

        val Lock: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_lock_bold)

        val Mic: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_mic_bold)

        val NotificationsActive: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_notifications_active_bold)

        val Pause: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_pause_bold)

        val PlayArrow: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_play_arrow_bold)

        val PushPin: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_push_pin_bold)

        val RadioButtonChecked: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_radio_button_checked_bold)

        val TaskAlt: ImageVector
            @Composable get() = ImageVector.vectorResource(R.drawable.ic_task_alt_bold)

    }
}
