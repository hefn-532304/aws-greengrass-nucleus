/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.packagemanager.converter;

import com.aws.iot.evergreen.packagemanager.models.ComponentArtifact;
import com.aws.iot.evergreen.packagemanager.models.PackageRecipe;
import com.aws.iot.evergreen.packagemanager.models.RecipeDependencyProperties;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.core.Is.is;


@ExtendWith({EGExtension.class, MockitoExtension.class})
class RecipeLoaderTest {

    @Test
    void GIVEN_a_recipe_file_with_all_fields_and_mocked_resolved_platform_WHEN_converts_THEN_fields_are_populated_correctly()
            throws Exception {

        // GIVEN
        // read file
        String filename = "sample_recipe_with_all_fields.yaml";
        String recipeFileContent = new String(Files.readAllBytes(Paths.get(getClass().getResource(filename).toURI())));

        // WHEN
        Optional<PackageRecipe> optionalRecipe = RecipeLoader.loadFromFile(recipeFileContent);

        // THEN
        assertThat(optionalRecipe.isPresent(), is(true));

        PackageRecipe recipe = optionalRecipe.get();

        assertThat(recipe.getComponentName(), is("FooService"));
        assertThat(recipe.getVersion().getValue(), is("1.0.0"));
        assertThat(recipe.getComponentType().toLowerCase(), is("plugin"));

        // TODO enrich testing fields after making lifecycle section strongly typed
        assertThat(recipe.getLifecycle(), aMapWithSize(2));
        assertThat(recipe.getLifecycle(), hasEntry("install", "echo install"));
        assertThat(recipe.getLifecycle(), hasEntry("run", "echo run"));

        assertThat(recipe.getArtifacts().size(), is(2));
        ComponentArtifact artifact = recipe.getArtifacts().get(0);
        assertThat(artifact.getArtifactUri().toString(), is("s3://some-bucket/hello_world.py"));
        assertThat(artifact.getChecksum(), is("d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f"));
        assertThat(artifact.getAlgorithm(), is("SHA-256"));
        assertThat(recipe.getDependencies().size(), is(2));
        assertThat(recipe.getDependencies(), hasEntry("BarService", new RecipeDependencyProperties("^1.1", "soft")));

        assertThat(recipe.getDependencies(), hasEntry("BazService", new RecipeDependencyProperties("^2.0", "HARD")));

    }


    @Test
    void GIVEN_a_recipe_file_and_no_matching_platform_WHEN_converts_THEN_returns_empty() throws Exception {
        // GIVEN
        // read file
        String filename = "sample_recipe_with_no_matching_platform.yaml";
        String recipeFileContent = new String(Files.readAllBytes(Paths.get(getClass().getResource(filename).toURI())));

//         no matching platform
//        Mockito.when(platformResolver.findBestMatch(Mockito.anyList())).thenReturn(Optional.empty());

        // WHEN
        Optional<PackageRecipe> optionalRecipe = RecipeLoader.loadFromFile(recipeFileContent);

        // THEN
        assertThat(optionalRecipe.isPresent(), is(false));
    }
}