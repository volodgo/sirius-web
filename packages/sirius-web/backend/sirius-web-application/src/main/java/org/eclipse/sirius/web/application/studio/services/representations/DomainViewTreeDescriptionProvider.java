/*******************************************************************************
 * Copyright (c) 2024 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.application.studio.services.representations;

import java.util.Objects;
import java.util.UUID;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextProcessor;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.IDAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.view.TextStyleDescription;
import org.eclipse.sirius.components.view.TextStylePalette;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.builder.generated.tree.TreeBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.emf.tree.ITreeIdProvider;
import org.eclipse.sirius.components.view.tree.TreeDescription;
import org.eclipse.sirius.components.view.tree.TreeItemLabelDescription;
import org.eclipse.sirius.components.view.tree.TreeItemLabelElementDescription;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.eclipse.sirius.web.application.studio.services.api.IStudioCapableEditingContextPredicate;
import org.springframework.stereotype.Service;

/**
 * Used to provide the view tree description for Domain elements using tree DSL builders.
 *
 * @author Jerome Gout
 */
@Service
public class DomainViewTreeDescriptionProvider implements IEditingContextProcessor {

    public static final String DOMAIN_EXPLORER_DESCRIPTION_NAME = "Domain explorer by DSL";

    private static final String BLUE_ITALIC_TEXT_STYLE_NAME = "blue italic";

    private static final String BROWN_BOLD_TEXT_STYLE_NAME = "brown bold";

    private static final String NORMAL_TEXT_STYLE_NAME = "normal";

    private static final String DOMAIN_VIEW_EXPLORER_ID = "DomainExplorer";

    private static final String AQL_TRUE = "aql:true";

    private final IStudioCapableEditingContextPredicate studioCapableEditingContextPredicate;

    private final View view;

    private final ITreeIdProvider treeIdProvider;

    private TreeDescription viewDescription;

    public DomainViewTreeDescriptionProvider(IStudioCapableEditingContextPredicate studioCapableEditingContextPredicate, ITreeIdProvider treeIdProvider) {
        this.studioCapableEditingContextPredicate = Objects.requireNonNull(studioCapableEditingContextPredicate);
        this.treeIdProvider = Objects.requireNonNull(treeIdProvider);
        this.view = this.createView();
    }

    @Override
    public void preProcess(IEditingContext editingContext) {
        if (editingContext instanceof EditingContext siriusWebEditingContext && this.studioCapableEditingContextPredicate.test(editingContext)) {
            siriusWebEditingContext.getViews().add(this.view);
        }
    }

    private View createView() {
        var domainTextStylePalette = this.createTextStylePalette();
        var domainExplorerDescription = this.domainExplorerDescription(domainTextStylePalette);

        var domainView = new ViewBuilders()
                .newView()
                .textStylePalettes(domainTextStylePalette)
                .build();

        domainView.getDescriptions().add(domainExplorerDescription);

        domainView.eAllContents().forEachRemaining(eObject -> {
            var id = UUID.nameUUIDFromBytes(EcoreUtil.getURI(eObject).toString().getBytes());
            eObject.eAdapters().add(new IDAdapter(id));
        });

        String resourcePath = UUID.nameUUIDFromBytes(DOMAIN_VIEW_EXPLORER_ID.getBytes()).toString();
        JsonResource resource = new JSONResourceFactory().createResourceFromPath(resourcePath);
        resource.eAdapters().add(new ResourceMetadataAdapter(DOMAIN_VIEW_EXPLORER_ID));
        resource.getContents().add(domainView);

        return domainView;
    }

    public String getRepresentationDescriptionId() {
        return this.treeIdProvider.getId(this.viewDescription);
    }

    private TreeDescription domainExplorerDescription(TextStylePalette domainTextStylePalette) {
        this.viewDescription = new TreeBuilders()
                .newTreeDescription()
                .name(DOMAIN_EXPLORER_DESCRIPTION_NAME)
                .domainType("domain::Domain")
                .titleExpression("aql:'Domain explorer DSL'")
                .kindExpression("aql:self.getKind()")
                .iconURLExpression("aql:self.getIconURL()")
                .deletableExpression("aql:self.isDeletable()")
                .editableExpression("aql:self.isEditable()")
                .selectableExpression("aql:self.isSelectable()")
                .hasChildrenExpression("aql:self.hasChildren()")
                .elementsExpression("aql:editingContext.getElements()")
                .childrenExpression("aql:self.getChildren(editingContext, expanded)")
                .parentExpression("aql:self.getParent(editingContext, id)")
                .treeItemIdExpression("aql:self.getTreeItemId()")
                .treeItemObjectExpression("aql:editingContext.getTreeItemObject(id)")
                .preconditionExpression("aql:false") // -> set canCreate to false to avoid to be display in New representation menu
                .treeItemLabelDescriptions(this.entityStyle(domainTextStylePalette), this.attributeStyle(domainTextStylePalette), this.defaultStyle())
                .build();
        return this.viewDescription;
    }


    private TextStylePalette createTextStylePalette() {
        return new ViewBuilders()
                .newTextStylePalette()
                .name("Domain Text Style Palette")
                .styles(this.getBrownBoldStyle(), this.getBlueItalicStyle(), this.getNormalStyle())
                .build();
    }

    private TreeItemLabelDescription attributeStyle(TextStylePalette textStylePalette) {
        return new TreeBuilders()
                .newTreeItemLabelDescription()
                .name("attribute style")
                .preconditionExpression("aql:self.oclIsKindOf(domain::Attribute)")
                .children(this.getAttributeKeyFragment(textStylePalette), this.getAttributeValueFragment(textStylePalette))
                .build();
    }

    private TreeItemLabelDescription entityStyle(TextStylePalette textStylePalette) {
        return new TreeBuilders()
                .newTreeItemLabelDescription()
                .name("entity style")
                .preconditionExpression("aql:self.oclIsKindOf(domain::Entity)")
                .children(this.getEntityKeyFragment(textStylePalette), this.getEntityValueFragment(textStylePalette))
                .build();
    }

    private TreeItemLabelDescription defaultStyle() {
        return new TreeBuilders()
                .newTreeItemLabelDescription()
                .name("default style")
                .preconditionExpression("aql:true")
                .children(new TreeBuilders()
                        .newTreeItemLabelFragmentDescription()
                        .labelExpression("aql:self.getTreeItemLabel()")
                        // no style specified => default one will be chosen
                        .build()
                        )
                .build();
    }

    private TreeItemLabelElementDescription getEntityValueFragment(TextStylePalette textStylePalette) {
        return new TreeBuilders().newTreeItemLabelFragmentDescription()
                .labelExpression("aql:self.getTreeItemLabel()")
                .style(this.getTextStyleByName(textStylePalette, NORMAL_TEXT_STYLE_NAME))
                .build();
    }

    private TreeItemLabelElementDescription getEntityKeyFragment(TextStylePalette textStylePalette) {
        return new TreeBuilders().newTreeItemLabelFragmentDescription()
                .labelExpression("aql:'[Entity] '")
                .style(this.getTextStyleByName(textStylePalette, BROWN_BOLD_TEXT_STYLE_NAME))
                .build();
    }

    private TreeItemLabelElementDescription getAttributeValueFragment(TextStylePalette textStylePalette) {
        return new TreeBuilders().newTreeItemLabelFragmentDescription()
                .labelExpression("aql:self.name")
                .style(this.getTextStyleByName(textStylePalette, NORMAL_TEXT_STYLE_NAME))
                .build();
    }

    private TreeItemLabelElementDescription getAttributeKeyFragment(TextStylePalette textStylePalette) {
        return new TreeBuilders().newTreeItemLabelFragmentDescription()
                .labelExpression("aql:'[Attribute] '")
                .style(this.getTextStyleByName(textStylePalette, BLUE_ITALIC_TEXT_STYLE_NAME))
                .build();
    }

    private TextStyleDescription getBlueItalicStyle() {
        return new ViewBuilders().newTextStyleDescription()
                .name(BLUE_ITALIC_TEXT_STYLE_NAME)
                .foregroundColorExpression("aql:'#6584e2'")
                .isItalicExpression(AQL_TRUE)
                .build();
    }

    private TextStyleDescription getBrownBoldStyle() {
        return new ViewBuilders().newTextStyleDescription()
                .name(BROWN_BOLD_TEXT_STYLE_NAME)
                .foregroundColorExpression("aql:'#c29e00'")
                .isBoldExpression(AQL_TRUE)
                .build();
    }

    private TextStyleDescription getNormalStyle() {
        return new ViewBuilders().newTextStyleDescription()
                .name(NORMAL_TEXT_STYLE_NAME)
                .build();
    }

    private TextStyleDescription getTextStyleByName(TextStylePalette textStylePalette, String styleName) {
        return textStylePalette.getStyles().stream()
                .filter(tsd -> tsd.getName().equals(styleName))
                .findFirst()
                .orElse(null);
    }
}
