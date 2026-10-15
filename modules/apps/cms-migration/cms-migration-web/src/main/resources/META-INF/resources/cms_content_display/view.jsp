<%--
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
--%>

<%@ include file="/init.jsp" %>

<%
CMSContentDisplayDisplayContext cmsContentDisplayDisplayContext = (CMSContentDisplayDisplayContext)request.getAttribute(WebKeys.PORTLET_DISPLAY_CONTEXT);

ObjectEntry objectEntry = cmsContentDisplayDisplayContext.getObjectEntry();
%>

<c:choose>
	<c:when test="<%= objectEntry != null %>">

		<%
		cmsContentDisplayDisplayContext.render(objectEntry, request, PipingServletResponseFactory.createPipingServletResponse(pageContext));
		%>

	</c:when>
	<c:otherwise>

		<%
		renderRequest.setAttribute(WebKeys.PORTLET_CONFIGURATOR_VISIBILITY, Boolean.TRUE);
		%>

	</c:otherwise>
</c:choose>