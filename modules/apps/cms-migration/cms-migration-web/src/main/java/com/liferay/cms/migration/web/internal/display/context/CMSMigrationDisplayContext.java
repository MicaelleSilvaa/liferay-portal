/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.display.context;

import com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationReport;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatus;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskStatusRegistryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lino Alves
 */
public class CMSMigrationDisplayContext {

	public CMSMigrationDisplayContext(
		BackgroundTaskManager backgroundTaskManager,
		DepotEntryLocalService depotEntryLocalService,
		GroupLocalService groupLocalService,
		HttpServletRequest httpServletRequest, RenderRequest renderRequest,
		RenderResponse renderResponse) {

		_backgroundTaskManager = backgroundTaskManager;
		_depotEntryLocalService = depotEntryLocalService;
		_groupLocalService = groupLocalService;
		_renderRequest = renderRequest;
		_renderResponse = renderResponse;

		_themeDisplay = (ThemeDisplay)httpServletRequest.getAttribute(
			WebKeys.THEME_DISPLAY);
	}

	public List<BackgroundTask> getBackgroundTasks() {
		return ListUtil.sort(
			_backgroundTaskManager.getBackgroundTasks(
				_themeDisplay.getCompanyGroupId(),
				CMSMigrationBackgroundTaskExecutor.class.getName()),
			(backgroundTask1, backgroundTask2) -> Long.compare(
				backgroundTask2.getBackgroundTaskId(),
				backgroundTask1.getBackgroundTaskId()));
	}

	public CMSMigrationReport getCMSMigrationReport(
		BackgroundTask backgroundTask) {

		String statusMessage = backgroundTask.getStatusMessage();

		if (Validator.isNull(statusMessage)) {
			return new CMSMigrationReport();
		}

		try {
			return CMSMigrationReport.of(
				JSONFactoryUtil.createJSONObject(statusMessage));
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			CMSMigrationReport cmsMigrationReport = new CMSMigrationReport();

			cmsMigrationReport.addError(statusMessage);

			return cmsMigrationReport;
		}
	}

	public List<Group> getGroups() {
		List<Group> groups = new ArrayList<>();

		for (Group group :
				_groupLocalService.getActiveGroups(
					_themeDisplay.getCompanyId(), true, true, QueryUtil.ALL_POS,
					QueryUtil.ALL_POS, null)) {

			if (group.isCompany() || group.isDepot() ||
				group.isStagingGroup() || !group.isSite()) {

				continue;
			}

			groups.add(group);
		}

		return groups;
	}

	public int getProgress(BackgroundTask backgroundTask) {
		BackgroundTaskStatus backgroundTaskStatus =
			BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(
				backgroundTask.getBackgroundTaskId());

		if (backgroundTaskStatus == null) {
			return 0;
		}

		long total = GetterUtil.getLong(
			backgroundTaskStatus.getAttribute("total"));

		if (total == 0) {
			return 0;
		}

		long counter = GetterUtil.getLong(
			backgroundTaskStatus.getAttribute("counter"));

		return (int)Math.min(100, Math.round((double)counter / total * 100));
	}

	public RenderRequest getRenderRequest() {
		return _renderRequest;
	}

	public RenderResponse getRenderResponse() {
		return _renderResponse;
	}

	public List<DepotEntry> getSpaces() {
		return _depotEntryLocalService.getDepotEntries(
			_themeDisplay.getCompanyId(), DepotConstants.TYPE_SPACE);
	}

	public JSONObject getStatusJSONObject(BackgroundTask backgroundTask)
		throws Exception {

		BackgroundTaskStatus backgroundTaskStatus =
			BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(
				backgroundTask.getBackgroundTaskId());

		if (backgroundTaskStatus == null) {
			return JSONFactoryUtil.createJSONObject();
		}

		return JSONFactoryUtil.createJSONObject(
			backgroundTaskStatus.getAttributesJSON());
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CMSMigrationDisplayContext.class);

	private final BackgroundTaskManager _backgroundTaskManager;
	private final DepotEntryLocalService _depotEntryLocalService;
	private final GroupLocalService _groupLocalService;
	private final RenderRequest _renderRequest;
	private final RenderResponse _renderResponse;
	private final ThemeDisplay _themeDisplay;

}