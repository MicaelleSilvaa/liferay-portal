/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.portlet;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationPortletKeys;
import com.liferay.cms.migration.web.internal.display.context.CMSMigrationDisplayContext;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.Portlet;
import jakarta.portlet.PortletException;
import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Lino Alves
 */
@Component(
	property = {
		"com.liferay.portlet.add-default-resource=true",
		"com.liferay.portlet.display-category=category.hidden",
		"com.liferay.portlet.render-weight=50",
		"jakarta.portlet.display-name=CMS Migration",
		"jakarta.portlet.expiration-cache=0",
		"jakarta.portlet.init-param.template-path=/META-INF/resources/",
		"jakarta.portlet.init-param.view-template=/view.jsp",
		"jakarta.portlet.name=" + CMSMigrationPortletKeys.CMS_MIGRATION,
		"jakarta.portlet.resource-bundle=content.Language",
		"jakarta.portlet.security-role-ref=administrator",
		"jakarta.portlet.version=4.0"
	},
	service = Portlet.class
)
public class CMSMigrationPortlet extends MVCPortlet {

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		renderRequest.setAttribute(
			WebKeys.PORTLET_DISPLAY_CONTEXT,
			new CMSMigrationDisplayContext(
				_backgroundTaskManager, _depotEntryLocalService,
				_groupLocalService,
				_portal.getHttpServletRequest(renderRequest), renderRequest,
				renderResponse));

		super.render(renderRequest, renderResponse);
	}

	@Reference
	private BackgroundTaskManager _backgroundTaskManager;

	@Reference
	private DepotEntryLocalService _depotEntryLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private Portal _portal;

}