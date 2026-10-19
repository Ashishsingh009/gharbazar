package com.ashish.gharbazaar.a2ui.runtime

import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.a2ui.model.protocol.A2uiUserAction
import com.ashish.gharbazaar.domain.evaluateAction

class AllowlistInterceptor(
    private val catalogJson: String,
    private val installationId: () -> String?,
) : A2uiActionInterceptor {
    override suspend fun onInterceptAction(action: A2uiUserAction): A2uiUserAction? {
        val name = (action as? A2uiEventAction)?.eventName ?: return null
        val decision = evaluateAction(
            catalogJson = catalogJson,
            name = name,
            installationId = installationId(),
            nativeConfirmation = false,
            clientSequence = 1,
            serverSequence = 1,
        )
        return if (decision.allowed) action else null
    }
}
