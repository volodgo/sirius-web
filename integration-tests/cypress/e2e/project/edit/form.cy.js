/*******************************************************************************
 * Copyright (c) 2023 Obeo.
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
describe('/projects/:projectId/edit - FormDescriptionEditor', () => {
  beforeEach(() => {
    cy.deleteAllProjects();
  });

  it('check widget read-only mode in form', () => {
    // Create the view
    cy.createProjectFromTemplate('studio-template').then((res) => {
      const projectId = res.body.data.createProjectFromTemplate.project.id;
      const view_document_id = 'ea57f74d-bc7b-3a7a-81e0-8aef4ee85770';
      cy.createDocument(projectId, view_document_id, 'ViewDocument').then(() => {
        cy.visit(`/projects/${projectId}/edit`);
      });
    });
    cy.getByTestId('ViewDocument').dblclick();
    cy.getByTestId('View').dblclick();
    cy.getByTestId('View-more').click();
    cy.getByTestId('treeitem-contextmenu').findByTestId('new-object').click();
    cy.getByTestId('create-object').should('be.enabled');
    cy.getByTestId('childCreationDescription').click();
    cy.get('[data-value="Form Description"]').click();
    cy.getByTestId('create-object').click();
    cy.getByTestId('New Form Description').click();
    cy.getByTestId('Domain Type').type('flow::System');
    cy.getByTestId('Name').type('{selectall}').type('ReadOnlyRepresentation');
    cy.getByTestId('Title Expression').type('{selectall}').type('ReadOnlyRepresentation');
    cy.getByTestId('ReadOnlyRepresentation').dblclick();
    cy.getByTestId('PageDescription').dblclick();
    cy.getByTestId('GroupDescription-more').click();
    cy.getByTestId('treeitem-contextmenu').findByTestId('new-object').click();
    cy.getByTestId('childCreationDescription').children('[role="button"]').invoke('text').should('have.length.gt', 1);
    cy.getByTestId('childCreationDescription').click().get('[data-value="Widgets Button Description"]').should('exist').click();
    cy.getByTestId('create-object').click();
    cy.getByTestId('Button Label Expression').type('Test Button');
    cy.getByTestId('Is Enabled Expression').type('aql:self.temperature==0');

    cy.get('[title="Back to the homepage"]').click();
    // Check the representation
    cy.getByTestId('create-template-Flow').click();

    cy.getByTestId('NewSystem').dblclick();
    cy.getByTestId('NewSystem-more').click();

    cy.getByTestId('treeitem-contextmenu').findByTestId('new-representation').click();
    cy.getByTestId('representationDescription').children('[role="button"]').invoke('text').should('have.length.gt', 1);
    cy.getByTestId('representationDescription').click();
    cy.getByTestId('ReadOnlyRepresentation').should('exist').click();
    cy.getByTestId('create-representation').click();

    cy.getByTestId('Test Button').should('exist').should('not.be.disabled');
    cy.getByTestId('NewSystem').click();
    cy.getByTestId('Temperature').type('{selectall}').type('2').type('{enter}');
    cy.getByTestId('Test Button').should('be.disabled');
    cy.getByTestId('Temperature').type('{selectall}').type('0').type('{enter}');
    cy.getByTestId('Test Button').should('not.be.disabled');
  });
});