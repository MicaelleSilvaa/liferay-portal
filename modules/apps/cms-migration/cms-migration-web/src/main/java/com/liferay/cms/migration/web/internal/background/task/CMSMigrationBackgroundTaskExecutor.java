/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.background.task;

import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationManager;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationReport;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusMessageTranslator;
import com.liferay.portal.kernel.backgroundtask.BaseBackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.backgroundtask.display.BackgroundTaskDisplay;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.GetterUtil;

import java.io.Serializable;

import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Lino Alves
 */
@Component(
	property = "background.task.executor.class.name=com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor",
	service = BackgroundTaskExecutor.class
)
public class CMSMigrationBackgroundTaskExecutor
	extends BaseBackgroundTaskExecutor {

	public CMSMigrationBackgroundTaskExecutor() {
		setIsolationLevel(BackgroundTaskConstants.ISOLATION_LEVEL_COMPANY);
	}

	@Override
	public BackgroundTaskExecutor clone() {
		return this;
	}

	@Override
	public BackgroundTaskResult execute(BackgroundTask backgroundTask)
		throws Exception {

		Map<String, Serializable> taskContextMap =
			backgroundTask.getTaskContextMap();

		User user = _userLocalService.getUser(backgroundTask.getUserId());

		Group sourceGroup = _groupLocalService.getGroup(
			GetterUtil.getLong(taskContextMap.get("sourceGroupId")));

		DepotEntry depotEntry = _depotEntryLocalService.getDepotEntry(
			GetterUtil.getLong(taskContextMap.get("depotEntryId")));

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);
		serviceContext.setCompanyId(user.getCompanyId());
		serviceContext.setLanguageId(sourceGroup.getDefaultLanguageId());
		serviceContext.setScopeGroupId(sourceGroup.getGroupId());
		serviceContext.setUserId(user.getUserId());

		String name = PrincipalThreadLocal.getName();
		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		CMSMigrationBackgroundTaskStatusMessageSender
			cmsMigrationBackgroundTaskStatusMessageSender =
				new CMSMigrationBackgroundTaskStatusMessageSender();
		CMSMigrationManager cmsMigrationManager = new CMSMigrationManager();
		CMSMigrationReport cmsMigrationReport = new CMSMigrationReport();

		try {
			PrincipalThreadLocal.setName(user.getUserId());
			PermissionThreadLocal.setPermissionChecker(
				_permissionCheckerFactory.create(user));
			ServiceContextThreadLocal.pushServiceContext(serviceContext);

			cmsMigrationManager.migrate(
				new CMSMigrationContext(
					depotEntry, depotEntry.getGroup(),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateBlogsEntries")),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateFileEntries")),
					GetterUtil.getBoolean(
						taskContextMap.get("migrateJournalArticles")),
					cmsMigrationReport,
					GetterUtil.getBoolean(taskContextMap.get("rewritePages")),
					serviceContext, sourceGroup,
					cmsMigrationBackgroundTaskStatusMessageSender::
						sendItemStatusMessage,
					user.getUserId()));
		}
		catch (Exception exception) {
			cmsMigrationReport.addError(
				"The migration stopped: " + exception.getMessage());
		}
		finally {
			PrincipalThreadLocal.setName(name);
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
			ServiceContextThreadLocal.popServiceContext();
		}

		int status = BackgroundTaskConstants.STATUS_SUCCESSFUL;

		if (cmsMigrationReport.hasErrors()) {
			status = BackgroundTaskConstants.STATUS_COMPLETED_WITH_ERRORS;
		}

		return new BackgroundTaskResult(
			status,
			cmsMigrationReport.toJSONObject(
			).toString());
	}

	@Override
	public BackgroundTaskDisplay getBackgroundTaskDisplay(
		BackgroundTask backgroundTask) {

		return null;
	}

	@Override
	public BackgroundTaskStatusMessageTranslator
		getBackgroundTaskStatusMessageTranslator() {

		return _backgroundTaskStatusMessageTranslator;
	}

	private final BackgroundTaskStatusMessageTranslator
		_backgroundTaskStatusMessageTranslator =
			new CMSMigrationBackgroundTaskStatusMessageTranslator();

	@Reference
	private DepotEntryLocalService _depotEntryLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private PermissionCheckerFactory _permissionCheckerFactory;

	@Reference
	private UserLocalService _userLocalService;

}