/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.display.context;

import com.liferay.cms.migration.web.internal.info.item.renderer.CMSMigrationInfoItemRenderer;
import com.liferay.cms.migration.web.internal.reference.InfoItemReferenceMapper;
import com.liferay.info.item.renderer.InfoItemRenderer;
import com.liferay.info.item.renderer.InfoItemRendererRegistry;
import com.liferay.info.item.renderer.InfoItemTemplatedRenderer;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;

import jakarta.portlet.PortletPreferences;
import jakarta.portlet.RenderRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Lino Alves
 */
public class CMSContentDisplayDisplayContext {

	public CMSContentDisplayDisplayContext(
		InfoItemRendererRegistry infoItemRendererRegistry,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectEntryService objectEntryService, RenderRequest renderRequest) {

		_infoItemRendererRegistry = infoItemRendererRegistry;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectEntryService = objectEntryService;

		PortletPreferences portletPreferences = renderRequest.getPreferences();

		_objectEntryId = GetterUtil.getLong(
			portletPreferences.getValue("objectEntryId", null));
		_templateEntryId = GetterUtil.getLong(
			portletPreferences.getValue("templateEntryId", null));
	}

	public ObjectEntry getObjectEntry() {
		if (_objectEntryId <= 0) {
			return null;
		}

		try {
			return _objectEntryService.getObjectEntry(_objectEntryId);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return null;
		}
	}

	public void render(
		ObjectEntry objectEntry, HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		if (_templateEntryId > 0) {
			InfoItemRenderer<Object> infoItemRenderer =
				(InfoItemRenderer<Object>)
					_infoItemRendererRegistry.getInfoItemRenderer(
						InfoItemReferenceMapper.
							TEMPLATE_INFO_ITEM_RENDERER_KEY);

			if (infoItemRenderer instanceof InfoItemTemplatedRenderer) {
				InfoItemTemplatedRenderer<Object> infoItemTemplatedRenderer =
					(InfoItemTemplatedRenderer<Object>)infoItemRenderer;

				infoItemTemplatedRenderer.render(
					objectEntry, String.valueOf(_templateEntryId),
					httpServletRequest, httpServletResponse);

				return;
			}
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.fetchObjectDefinition(
				objectEntry.getObjectDefinitionId());

		InfoItemRenderer<Object> infoItemRenderer =
			(InfoItemRenderer<Object>)
				_infoItemRendererRegistry.getInfoItemRenderer(
					CMSMigrationInfoItemRenderer.getKey(
						CMSMigrationInfoItemRenderer.STYLE_ABSTRACT));

		if ((objectDefinition != null) && (infoItemRenderer != null)) {
			infoItemRenderer.render(
				objectEntry, httpServletRequest, httpServletResponse);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CMSContentDisplayDisplayContext.class);

	private final InfoItemRendererRegistry _infoItemRendererRegistry;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final long _objectEntryId;
	private final ObjectEntryService _objectEntryService;
	private final long _templateEntryId;

}