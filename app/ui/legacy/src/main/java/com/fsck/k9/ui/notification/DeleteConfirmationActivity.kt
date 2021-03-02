package com.fsck.k9.ui.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.controller.MessageReferenceHelper
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener
import com.fsck.k9.notification.NotificationActionService
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.K9ActivityCommon
import com.fsck.k9.ui.base.ThemeType

class DeleteConfirmationActivity : AppCompatActivity(), ConfirmationDialogFragmentListener {
    private val base = K9ActivityCommon(this, ThemeType.DIALOG)

    private lateinit var account: Account
    private lateinit var messagesToDelete: List<MessageReference>

    public override fun onCreate(savedInstanceState: Bundle?) {
        base.preOnCreate()
        super.onCreate(savedInstanceState)

        extractExtras()

        if (savedInstanceState == null) {
            val dialogFragment = createConfirmationDialogFragment()
            dialogFragment.show(supportFragmentManager, DIALOG_TAG)
        }
    }

    override fun onResume() {
        base.preOnResume()
        super.onResume()
    }

    private fun extractExtras() {
        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT_UUID)
        val messageReferenceStrings = intent.getStringArrayListExtra(EXTRA_MESSAGE_REFERENCES)
        val messagesToDelete = MessageReferenceHelper.toMessageReferenceList(messageReferenceStrings)

        requireNotNull(accountUuid) { "$EXTRA_ACCOUNT_UUID can't be null" }
        requireNotNull(messagesToDelete) { "$EXTRA_MESSAGE_REFERENCES can't be null" }
        require(messagesToDelete.isNotEmpty()) { "$EXTRA_MESSAGE_REFERENCES can't be empty" }

        val account = getAccountFromUuid(accountUuid) ?: error("$EXTRA_ACCOUNT_UUID couldn't be resolved to an account")

        this.account = account
        this.messagesToDelete = messagesToDelete
    }

    private fun getAccountFromUuid(accountUuid: String): Account? {
        val preferences = Preferences.getPreferences(this)
        return preferences.getAccount(accountUuid)
    }

    private fun createConfirmationDialogFragment(): DialogFragment {
        val messageCount = messagesToDelete.size
        val message = resources.getQuantityString(R.plurals.dialog_confirm_delete_messages, messageCount, messageCount)

        return ConfirmationDialogFragment.newInstance(
            DIALOG_ID,
            getString(R.string.dialog_confirm_delete_title),
            message,
            getString(R.string.dialog_confirm_delete_confirm_button),
            getString(R.string.dialog_confirm_delete_cancel_button)
        )
    }

    override fun doPositiveClick(dialogId: Int) {
        deleteAndFinish()
    }

    override fun doNegativeClick(dialogId: Int) {
        finish()
    }

    override fun dialogCancelled(dialogId: Int) {
        finish()
    }

    private fun deleteAndFinish() {
        cancelNotifications()
        triggerDelete()
        finish()
    }

    private fun cancelNotifications() {
        val controller = MessagingController.getInstance(this)
        for (messageReference in messagesToDelete) {
            controller.cancelNotificationForMessage(account, messageReference)
        }
    }

    private fun triggerDelete() {
        val intent = NotificationActionService.createDeleteAllMessagesIntent(this, account.uuid, messagesToDelete)
        startService(intent)
    }

    companion object {
        private const val EXTRA_ACCOUNT_UUID = "accountUuid"
        private const val EXTRA_MESSAGE_REFERENCES = "messageReferences"
        private const val DIALOG_ID = 1
        private const val DIALOG_TAG = "dialog"

        @JvmStatic
        fun getIntent(context: Context, messageReference: MessageReference): Intent {
            return getIntent(context, listOf(messageReference))
        }

        @JvmStatic
        fun getIntent(context: Context, messageReferences: List<MessageReference>): Intent {
            val accountUuid = messageReferences[0].accountUuid
            val messageReferenceStrings = MessageReferenceHelper.toMessageReferenceStringList(messageReferences)

            return Intent(context, DeleteConfirmationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ACCOUNT_UUID, accountUuid)
                putExtra(EXTRA_MESSAGE_REFERENCES, messageReferenceStrings)
            }
        }
    }
}
