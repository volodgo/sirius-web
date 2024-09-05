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
package org.eclipse.sirius.web.application.controllers.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.jayway.jsonpath.JsonPath;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.sirius.components.collaborative.selection.dto.SelectionDialogTreeEventInput;
import org.eclipse.sirius.components.collaborative.trees.dto.TreeRefreshedEventPayload;
import org.eclipse.sirius.components.graphql.api.URLConstants;
import org.eclipse.sirius.components.graphql.tests.api.IGraphQLRequestor;
import org.eclipse.sirius.components.trees.Tree;
import org.eclipse.sirius.components.trees.TreeItem;
import org.eclipse.sirius.components.trees.tests.graphql.ExpandAllTreePathQueryRunner;
import org.eclipse.sirius.web.AbstractIntegrationTests;
import org.eclipse.sirius.web.data.PapayaIdentifiers;
import org.eclipse.sirius.web.tests.services.representation.RepresentationIdBuilder;
import org.eclipse.sirius.web.services.selection.SelectionDescriptionProvider;
import org.eclipse.sirius.web.tests.services.api.IGivenInitialServerState;
import org.eclipse.sirius.web.tests.services.selection.SelectionDialogTreeEventSubscriptionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

import graphql.execution.DataFetcherResult;
import reactor.test.StepVerifier;

/**
 * Integration tests of the selection controllers.
 *
 * @author sbegaudeau
 */
@Transactional
@SuppressWarnings("checkstyle:MultipleStringLiterals")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,  properties = { "sirius.web.test.enabled=studio" })
public class SelectionControllerIntegrationTests extends AbstractIntegrationTests {

    private static final String GET_SELECTION_DESCRIPTION = """
            query getSelectionDescription($editingContextId: ID!, $representationId: ID!, $targetObjectId: ID!) {
              viewer {
                editingContext(editingContextId: $editingContextId) {
                  representation(representationId: $representationId) {
                    description {
                      ... on SelectionDescription {
                          message(targetObjectId: $targetObjectId)
                          treeDescription {
                            id
                          }
                        }
                    }
                  }
                }
              }
            }
            """;

    private static final String GET_TREE_EVENT_SUBSCRIPTION = """
            subscription selectionDialogTreeEvent($input: SelectionDialogTreeEventInput!) {
              selectionDialogTreeEvent(input: $input) {
                __typename
                ... on TreeRefreshedEventPayload {
                  id
                  tree {
                    id
                    children {
                      iconURL
                    }
                  }
                }
              }
            }
            """;


    @Autowired
    private IGraphQLRequestor graphQLRequestor;

    @Autowired
    private IGivenInitialServerState givenInitialServerState;

    @Autowired
    private SelectionDialogTreeEventSubscriptionRunner selectionDialogTreeEventSubscriptionRunner;

    @Autowired
    private SelectionDescriptionProvider selectionDescriptionProvider;

    @Autowired
    private ExpandAllTreePathQueryRunner expandAllTreePathQueryRunner;

    @Autowired
    private RepresentationIdBuilder representationIdBuilder;

    @BeforeEach
    public void beforeEach() {
        this.givenInitialServerState.initialize();
    }

    @Test
    @DisplayName("Given a semantic object and a dialog description id, when requesting the Selection Description, then the Selection Description is sent")
    @Sql(scripts = { "/scripts/papaya.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = { "/scripts/cleanup.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void givenSemanticObjectWhenRequestingSelectionDescriptionThenTheSelectionDescriptionIsSent() {
        String representationId = "selectionDialog://?representationDescription=" + URLEncoder.encode(this.selectionDescriptionProvider.getSelectionDialogDescriptionId(), StandardCharsets.UTF_8);
        Map<String, Object> variables = Map.of("editingContextId", PapayaIdentifiers.PAPAYA_PROJECT.toString(), "representationId", representationId, "targetObjectId",
                PapayaIdentifiers.PROJECT_OBJECT.toString());
        var result = this.graphQLRequestor.execute(GET_SELECTION_DESCRIPTION, variables);
        String message = JsonPath.read(result, "$.data.viewer.editingContext.representation.description.message");
        String treeDescriptionId = JsonPath.read(result, "$.data.viewer.editingContext.representation.description.treeDescription.id");

        assertThat(message).isEqualTo("Select the objects to consider");
        assertThat(treeDescriptionId).isEqualTo(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId());
    }

    @Test
    @DisplayName("Given a semantic object, when we subscribe to its selection dialog tree, then the tree is sent")
    @Sql(scripts = {"/scripts/papaya.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = {"/scripts/cleanup.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void givenSemanticObjectWhenWeSubscribeToItsSelectionEventsThenTheSelectionIsSent() {
        var representationId = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of());
        var input = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationId);
        var flux = this.selectionDialogTreeEventSubscriptionRunner.run(input);
        var hasResourceRootContent = this.getTreeRefreshedEventPayloadMatcher();
        StepVerifier.create(flux)
            .expectNextMatches(hasResourceRootContent)
            .thenCancel()
            .verify();
    }

    @Test
    @DisplayName("Given the selection dialog tree, when we expand the first item, then the children are sent")
    @Sql(scripts = {"/scripts/papaya.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = {"/scripts/cleanup.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void givenSelectionDialogTreeWhenWeExpandTheFirstItemThenChildrenAreSent() {
        var representationId = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of());
        var input = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationId);
        var flux = this.selectionDialogTreeEventSubscriptionRunner.run(input);

        var treeItemId = new AtomicReference<String>();

        Consumer<Object> initialTreeContentConsumer = this.getTreeSubscriptionConsumer(tree -> {
            assertThat(tree).isNotNull();
            assertThat(tree.getChildren()).hasSize(1);
            assertThat(tree.getChildren()).allSatisfy(treeItem -> assertThat(treeItem.getChildren()).isEmpty());

            treeItemId.set(tree.getChildren().get(0).getId());
        });

        StepVerifier.create(flux)
            .consumeNextWith(initialTreeContentConsumer)
            .thenCancel()
            .verify();

        var representationIdExpanded = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of(treeItemId.get()));
        var expandedTreeInput = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationIdExpanded);
        var expandedTreeFlux = this.selectionDialogTreeEventSubscriptionRunner.run(expandedTreeInput);
        var treeRefreshedEventPayloadExpandMatcher = this.getTreeRefreshedEventPayloadExpandMatcher();
        StepVerifier.create(expandedTreeFlux)
            .expectNextMatches(treeRefreshedEventPayloadExpandMatcher)
            .thenCancel()
            .verify();
    }


    @Test
    @DisplayName("Given the selection dialog tree, when we perform expand all on the first item, then the children are sent")
    @Sql(scripts = {"/scripts/papaya.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = {"/scripts/cleanup.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void givenSelectionDialogTreeWhenWePerformExpandAllOnTheFirstItemThenChildrenAreSent() {
        var representationId = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of());
        var input = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationId);
        var flux = this.selectionDialogTreeEventSubscriptionRunner.run(input);

        var treeItemId = new AtomicReference<String>();
        var treeInstanceId = new AtomicReference<String>();

        Consumer<Object> initialTreeContentConsumer = this.getTreeSubscriptionConsumer(tree -> {
            assertThat(tree).isNotNull();
            assertThat(tree.getChildren()).hasSize(1);
            assertThat(tree.getChildren()).allSatisfy(treeItem -> assertThat(treeItem.getChildren()).isEmpty());
            treeInstanceId.set(tree.getId());
            treeItemId.set(tree.getChildren().get(0).getId());
        });

        var treeItemIds = new AtomicReference<List<String>>();

        Runnable getTreePath = () -> {
            Map<String, Object> variables = Map.of(
                    "editingContextId", PapayaIdentifiers.PAPAYA_PROJECT.toString(),
                    "treeId", treeInstanceId.get(),
                    "treeItemId", treeItemId.get()
            );
            var result = this.expandAllTreePathQueryRunner.run(variables);
            List<String> treeItemIdsToExpand = JsonPath.read(result, "$.data.viewer.editingContext.expandAllTreePath.treeItemIdsToExpand");
            assertThat(treeItemIdsToExpand).isNotEmpty();

            treeItemIds.set(treeItemIdsToExpand);
        };

        StepVerifier.create(flux)
            .consumeNextWith(initialTreeContentConsumer)
            .then(getTreePath)
            .thenCancel()
            .verify(Duration.ofSeconds(10));

        Consumer<Object> initialExpandedTreeContentConsumer = this.getTreeSubscriptionConsumer(tree -> {
            assertThat(tree).isNotNull();
            assertThat(tree.getChildren()).hasSize(1);
            assertThat(tree.getChildren()).anySatisfy(treeItem -> assertThat(treeItem.getChildren()).isNotEmpty());

        });

        var representationIdExpanded = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), treeItemIds.get());
        var expandedTreeInput = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationIdExpanded);
        var expandedTreeFlux = this.selectionDialogTreeEventSubscriptionRunner.run(expandedTreeInput);

        StepVerifier.create(expandedTreeFlux)
                .consumeNextWith(initialExpandedTreeContentConsumer)
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Given the selection dialog tree, when we perform expand all on the second item (the root project), then the children are sent")
    @Sql(scripts = {"/scripts/papaya.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = {"/scripts/cleanup.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void givenSelectionDialogTreeWhenWePerformExpandAllOnTheSecondItemThenChildrenAreSent() {
        var representationId = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of());
        var input = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationId);
        var flux = this.selectionDialogTreeEventSubscriptionRunner.run(input);

        var rootTreeItemId = new AtomicReference<String>();
        var treeInstanceId = new AtomicReference<String>();

        Consumer<Object> initialTreeContentConsumer = this.getTreeSubscriptionConsumer(tree -> {
            assertThat(tree).isNotNull();
            assertThat(tree.getChildren()).hasSize(1);
            assertThat(tree.getChildren()).allSatisfy(treeItem -> assertThat(treeItem.getChildren()).isEmpty());
            treeInstanceId.set(tree.getId());
            rootTreeItemId.set(tree.getChildren().get(0).getId());
        });

        StepVerifier.create(flux)
            .consumeNextWith(initialTreeContentConsumer)
            .thenCancel()
            .verify(Duration.ofSeconds(10));

        //We expand the first tree item (representing the resource)
        var representationIdExpandedFirstTreeItem = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of(rootTreeItemId.get()));
        var expandedFirstTreeItemInput = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationIdExpandedFirstTreeItem);
        var expandedFirstTreeItemFlux = this.selectionDialogTreeEventSubscriptionRunner.run(expandedFirstTreeItemInput);

        //Used to retrieve the Papaya Root Project tree item
        var rootProjectTreeItemId = new AtomicReference<String>();
        Consumer<Object> firstTreeItemExpandedContentConsumer = this.getTreeSubscriptionConsumer(tree -> {
            assertThat(tree).isNotNull();
            assertThat(tree.getChildren()).hasSize(1);
            var rootResourceTreeItem = tree.getChildren().get(0);
            assertThat(tree.getChildren()).hasSize(1);
            rootProjectTreeItemId.set(rootResourceTreeItem.getChildren().get(0).getId());
        });

        var treeItemIds = new AtomicReference<List<String>>();

        Runnable getTreePath = () -> {
            Map<String, Object> variables = Map.of(
                    "editingContextId", PapayaIdentifiers.PAPAYA_PROJECT.toString(),
                    "treeId", treeInstanceId.get(),
                    "treeItemId", rootProjectTreeItemId.get()
                    );
            var result = this.expandAllTreePathQueryRunner.run(variables);
            List<String> treeItemIdsToExpand = JsonPath.read(result, "$.data.viewer.editingContext.expandAllTreePath.treeItemIdsToExpand");
            assertThat(treeItemIdsToExpand).isNotEmpty();
            treeItemIdsToExpand.add(0, rootTreeItemId.get());
            treeItemIds.set(treeItemIdsToExpand);
        };

        StepVerifier.create(expandedFirstTreeItemFlux)
            .consumeNextWith(firstTreeItemExpandedContentConsumer)
            //Now that we have expand and retrieve the Papaya Root Project tree item, we can perform the expandAll from it
            .then(getTreePath)
            .thenCancel()
            .verify(Duration.ofSeconds(10));


        Consumer<Object> initialExpandedTreeContentConsumer = this.getTreeSubscriptionConsumer(tree -> {
            assertThat(tree).isNotNull();
            assertThat(tree.getChildren()).hasSize(1);
            TreeItem rootProjectTreeItem = tree.getChildren().get(0);
            assertThat(rootProjectTreeItem.getChildren()).isNotEmpty();
        });

        var representationIdExpanded = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), treeItemIds.get());
        var expandedTreeInput = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationIdExpanded);
        var expandedTreeFlux = this.selectionDialogTreeEventSubscriptionRunner.run(expandedTreeInput);
        // We now verify the expandAll result
        StepVerifier.create(expandedTreeFlux)
                .consumeNextWith(initialExpandedTreeContentConsumer)
                .thenCancel()
                .verify(Duration.ofSeconds(10));


    }

    private Predicate<Object> getTreeRefreshedEventPayloadMatcher() {
        Predicate<Tree> treeMatcher = tree -> tree.getChildren().size() == 1 && tree.getChildren().get(0).getIconURL().get(0).equals("/icons/Resource.svg")
                && tree.getChildren().get(0).getLabel().toString().equals("Sirius Web Architecture") && tree.getChildren().get(0).getChildren().isEmpty();

        return object -> Optional.of(object)
                .filter(DataFetcherResult.class::isInstance)
                .map(DataFetcherResult.class::cast)
                .map(DataFetcherResult::getData)
                .filter(TreeRefreshedEventPayload.class::isInstance)
                .map(TreeRefreshedEventPayload.class::cast)
                .map(TreeRefreshedEventPayload::tree)
                .filter(treeMatcher)
                .isPresent();
    }

    private Predicate<Object> getTreeRefreshedEventPayloadExpandMatcher() {
        Predicate<Tree> treeMatcher = tree -> tree.getChildren().size() == 1 && !tree.getChildren().get(0).getChildren().isEmpty();

        return object -> Optional.of(object)
                .filter(DataFetcherResult.class::isInstance)
                .map(DataFetcherResult.class::cast)
                .map(DataFetcherResult::getData)
                .filter(TreeRefreshedEventPayload.class::isInstance)
                .map(TreeRefreshedEventPayload.class::cast)
                .map(TreeRefreshedEventPayload::tree)
                .filter(treeMatcher)
                .isPresent();
    }

    @Test
    @DisplayName("Given a semantic object, when we subscribe to its selection dialog tree, then the URL of its treeItems is valid")
    @Sql(scripts = {"/scripts/papaya.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = {"/scripts/cleanup.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void givenSemanticObjectWhenWeSubscribeToItsSelectionEventsThenTheURLOfItsObjectsIsValid() {
        var representationId = representationIdBuilder.buildSelectionRepresentationId(this.selectionDescriptionProvider.getSelectionDialogTreeDescriptionId(), PapayaIdentifiers.PROJECT_OBJECT.toString(), List.of());
        var input = new SelectionDialogTreeEventInput(UUID.randomUUID(), PapayaIdentifiers.PAPAYA_PROJECT.toString(), representationId);
        var flux = this.graphQLRequestor.subscribeToSpecification(GET_TREE_EVENT_SUBSCRIPTION, input);

        Consumer<String> treeContentConsumer = payload -> Optional.of(payload)
                .ifPresentOrElse(body -> {
                    String typename = JsonPath.read(body, "$.data.selectionDialogTreeEvent.__typename");
                    assertThat(typename).isEqualTo(TreeRefreshedEventPayload.class.getSimpleName());

                    List<List<String>> objectIconURLs = JsonPath.read(body, "$.data.selectionDialogTreeEvent.tree.children[*].iconURL");
                    assertThat(objectIconURLs)
                            .isNotEmpty()
                            .allSatisfy(iconURLs -> {
                                assertThat(iconURLs)
                                        .isNotEmpty()
                                        .hasSize(1)
                                        .allSatisfy(iconURL -> assertThat(iconURL).startsWith(URLConstants.IMAGE_BASE_PATH));
                            });
                }, () -> fail("Missing selection"));

        StepVerifier.create(flux)
                .consumeNextWith(treeContentConsumer)
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    private Consumer<Object> getTreeSubscriptionConsumer(Consumer<Tree> treeConsumer) {
        return object -> Optional.of(object)
                .filter(DataFetcherResult.class::isInstance)
                .map(DataFetcherResult.class::cast)
                .map(DataFetcherResult::getData)
                .filter(TreeRefreshedEventPayload.class::isInstance)
                .map(TreeRefreshedEventPayload.class::cast)
                .map(TreeRefreshedEventPayload::tree)
                .ifPresentOrElse(treeConsumer, () -> fail("Missing tree"));
    }
}
