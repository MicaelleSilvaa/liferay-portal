/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds or updates the object entry that replaces a classic content item. The
 * entry is keyed by an external reference code derived from its source, so a
 * second run updates the entry instead of duplicating it. The entry is written
 * by the user who runs the migration, because attachments are stored per
 * user, and then attributed to the author of the classic item.
 *
 * @author Lino Alves
 */
public class ObjectEntryWriter {

	public ObjectEntry write(
			CMSMigrationContext cmsMigrationContext, String className,
			long classPK, ObjectDefinition objectDefinition,
			long objectEntryFolderId, String externalReferenceCode, long userId,
			String defaultLanguageId, Map<String, Serializable> values,
			Map<String, String> friendlyURLMap, Date createDate,
			Date modifiedDate, boolean approved)
		throws PortalException {

		values.put("externalReferenceCode", externalReferenceCode);

		ServiceContext serviceContext =
			(ServiceContext)cmsMigrationContext.getServiceContext(
			).clone();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);

		AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
			className, classPK);

		if (assetEntry != null) {
			serviceContext.setAssetCategoryIds(assetEntry.getCategoryIds());
			serviceContext.setAssetTagNames(assetEntry.getTagNames());
		}

		if (MapUtil.isNotEmpty(friendlyURLMap)) {
			serviceContext.setAttribute(
				"friendlyUrlMap", new HashMap<>(friendlyURLMap));
		}

		serviceContext.setUserId(cmsMigrationContext.getUserId());

		if (approved) {
			serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);
		}
		else {
			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);
		}

		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			externalReferenceCode, cmsMigrationContext.getDepotGroupId(),
			objectDefinition.getObjectDefinitionId());

		if (objectEntry == null) {
			objectEntry = _objectEntryLocalService.addObjectEntry(
				cmsMigrationContext.getDepotGroupId(),
				serviceContext.getUserId(),
				objectDefinition.getObjectDefinitionId(), objectEntryFolderId,
				defaultLanguageId, values, serviceContext);
		}
		else {
			objectEntry = _objectEntryLocalService.updateObjectEntry(
				serviceContext.getUserId(), objectEntry.getObjectEntryId(),
				objectEntryFolderId, values, serviceContext);
		}

		User user = _userLocalService.fetchUser(userId);

		if ((user != null) && !user.isGuestUser()) {
			objectEntry.setUserId(user.getUserId());
			objectEntry.setUserName(user.getFullName());
		}

		if (createDate != null) {
			objectEntry.setCreateDate(createDate);
		}

		if (modifiedDate != null) {
			objectEntry.setModifiedDate(modifiedDate);
		}

		objectEntry = _objectEntryLocalService.updateObjectEntry(objectEntry);

		cmsMigrationContext.addObjectEntry(className, classPK, objectEntry);

		return objectEntry;
	}

	private static final Snapshot<AssetEntryLocalService>
		_assetEntryLocalServiceSnapshot = new Snapshot<>(
			ObjectEntryWriter.class, AssetEntryLocalService.class);
	private static final Snapshot<ObjectEntryLocalService>
		_objectEntryLocalServiceSnapshot = new Snapshot<>(
			ObjectEntryWriter.class, ObjectEntryLocalService.class);
	private static final Snapshot<UserLocalService> _userLocalServiceSnapshot =
		new Snapshot<>(ObjectEntryWriter.class, UserLocalService.class);

	private final AssetEntryLocalService _assetEntryLocalService =
		_assetEntryLocalServiceSnapshot.get();
	private final ObjectEntryLocalService _objectEntryLocalService =
		_objectEntryLocalServiceSnapshot.get();
	private final UserLocalService _userLocalService =
		_userLocalServiceSnapshot.get();

}