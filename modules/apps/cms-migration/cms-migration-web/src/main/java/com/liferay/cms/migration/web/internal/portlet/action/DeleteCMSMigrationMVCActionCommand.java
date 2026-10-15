/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.portlet.action;

import com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationPortletKeys;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;

import java.util.Objects;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Deletes the record of a finished migration. The migrated content stays.
 *
 * @author Lino Alves
 */
@Component(
	property = {
		"jakarta.portlet.name=" + CMSMigrationPortletKeys.CMS_MIGRATION,
		"mvc.command.name=/cms_migration/delete_cms_migration"
	},
	service = MVCActionCommand.class
)
public class DeleteCMSMigrationMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		PermissionChecker permissionChecker =
			themeDisplay.getPermissionChecker();

		if (!permissionChecker.isCompanyAdmin()) {
			throw new PrincipalException.MustBeCompanyAdmin(
				permissionChecker.getUserId());
		}

		BackgroundTask backgroundTask =
			_backgroundTaskManager.getBackgroundTask(
				ParamUtil.getLong(actionRequest, "backgroundTaskId"));

		if (!Objects.equals(
				backgroundTask.getTaskExecutorClassName(),
				CMSMigrationBackgroundTaskExecutor.class.getName()) ||
			(backgroundTask.getCompanyId() != themeDisplay.getCompanyId())) {

			throw new PrincipalException();
		}

		_backgroundTaskManager.deleteBackgroundTask(
			backgroundTask.getBackgroundTaskId());
	}

	@Reference
	private BackgroundTaskManager _backgroundTaskManager;

}