/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.portlet;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationPortletKeys;
import com.liferay.cms.migration.web.internal.display.context.CMSContentDisplayDisplayContext;
import com.liferay.info.item.renderer.InfoItemRendererRegistry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.Portlet;
import jakarta.portlet.PortletException;
import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Displays one migrated object entry with one information template. The
 * migration puts this widget where a Web Content Display widget displayed the
 * classic article, so the page renders the same markup.
 *
 * @author Lino Alves
 */
@Component(
	property = {
		"com.liferay.portlet.add-default-resource=true",
		"com.liferay.portlet.css-class-wrapper=portlet-journal-content",
		"com.liferay.portlet.display-category=category.cms",
		"com.liferay.portlet.instanceable=true",
		"com.liferay.portlet.layout-cacheable=true",
		"com.liferay.portlet.private-request-attributes=false",
		"com.liferay.portlet.private-session-attributes=false",
		"com.liferay.portlet.render-weight=50",
		"com.liferay.portlet.use-default-template=true",
		"jakarta.portlet.display-name=CMS Content Display",
		"jakarta.portlet.expiration-cache=0",
		"jakarta.portlet.init-param.template-path=/META-INF/resources/",
		"jakarta.portlet.init-param.view-template=/cms_content_display/view.jsp",
		"jakarta.portlet.name=" + CMSMigrationPortletKeys.CMS_CONTENT_DISPLAY,
		"jakarta.portlet.resource-bundle=content.Language",
		"jakarta.portlet.security-role-ref=guest,power-user,user",
		"jakarta.portlet.version=4.0"
	},
	service = Portlet.class
)
public class CMSContentDisplayPortlet extends MVCPortlet {

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		renderRequest.setAttribute(
			WebKeys.PORTLET_DISPLAY_CONTEXT,
			new CMSContentDisplayDisplayContext(
				_infoItemRendererRegistry, _objectDefinitionLocalService,
				_objectEntryService, renderRequest));

		super.render(renderRequest, renderResponse);
	}

	@Reference
	private InfoItemRendererRegistry _infoItemRendererRegistry;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryService _objectEntryService;

}