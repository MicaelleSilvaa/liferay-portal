/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.background.task;

import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatus;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusMessageTranslator;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.util.GetterUtil;

/**
 * @author Lino Alves
 */
public class CMSMigrationBackgroundTaskStatusMessageTranslator
	implements BackgroundTaskStatusMessageTranslator {

	@Override
	public void translate(
		BackgroundTaskStatus backgroundTaskStatus, Message message) {

		String messageType = message.getString("messageType");

		if (messageType.equals("item")) {
			_translateItemMessage(backgroundTaskStatus, message);
		}
		else if (messageType.equals("phase")) {
			_translatePhaseMessage(backgroundTaskStatus, message);
		}
	}

	private synchronized void _translateItemMessage(
		BackgroundTaskStatus backgroundTaskStatus, Message message) {

		long counter = GetterUtil.getLong(
			backgroundTaskStatus.getAttribute("counter"));

		backgroundTaskStatus.setAttribute("counter", ++counter);

		backgroundTaskStatus.setAttribute(
			"itemTitle", message.getString("itemTitle"));
	}

	private synchronized void _translatePhaseMessage(
		BackgroundTaskStatus backgroundTaskStatus, Message message) {

		backgroundTaskStatus.setAttribute("counter", 0L);
		backgroundTaskStatus.setAttribute("itemTitle", "");
		backgroundTaskStatus.setAttribute("phase", message.getString("phase"));
		backgroundTaskStatus.setAttribute("total", message.getLong("total"));
	}

}