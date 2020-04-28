package com.aws.iot.evergreen.packagemanager.plugins;

import com.aws.iot.evergreen.packagemanager.GreengrassPackageServiceClientFactory;
import com.aws.iot.evergreen.packagemanager.TestHelper;
import com.aws.iot.evergreen.packagemanager.models.PackageIdentifier;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import com.vdurmont.semver4j.Semver;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.greengrasspackagemanagement.AWSGreengrassPackageManagement;
import com.amazonaws.services.greengrasspackagemanagement.model.GetArtifactRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, EGExtension.class})
class GreengrassRepositoryDownloaderTest {

    @Mock
    private HttpURLConnection connection;

    @Mock
    private AWSGreengrassPackageManagement client;

    @Mock
    private GreengrassPackageServiceClientFactory clientFactory;

    private GreengrassRepositoryDownloader downloader;

    @Captor
    ArgumentCaptor<GetArtifactRequest> getArtifactRequestArgumentCaptor;

    @BeforeEach
    void beforeEach() {
        when(clientFactory.getPmsClient()).thenReturn(client);
        this.downloader = spy(new GreengrassRepositoryDownloader(clientFactory));
    }

    @Test
    void GIVEN_artifact_url_WHEN_attempt_download_THEN_task_succeed() throws Exception {
        AmazonServiceException ase = new AmazonServiceException("Redirect");
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "https://www.amazon.com/artifact.txt");
        ase.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
        ase.setHttpHeaders(headers);
        when(client.getArtifact(getArtifactRequestArgumentCaptor.capture())).thenThrow(ase);

        doReturn(connection).when(downloader).connect(any());
        when(connection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        Path mockArtifactPath = TestHelper.getPathForTestPackage(TestHelper.MONITORING_SERVICE_PACKAGE_NAME, "1.0.0")
                .resolve("monitor_artifact_100.txt");
        when(connection.getInputStream()).thenReturn(Files.newInputStream(mockArtifactPath));

        PackageIdentifier pkgId = new PackageIdentifier("CoolService", new Semver("1.0.0"), "CoolServiceARN");
        Path testCache = TestHelper.getPathForLocalTestCache();
        Path saveToPath = testCache.resolve("CoolService").resolve("1.0.0");
        Files.createDirectories(saveToPath);
        downloader.downloadToPath(pkgId, new URI("greengrass:artifactName"), saveToPath);

        GetArtifactRequest generatedRequest = getArtifactRequestArgumentCaptor.getValue();
        assertEquals("CoolServiceARN", generatedRequest.getPackageARN());
        assertEquals("artifactName", generatedRequest.getArtifactName());

        byte[] originalFile = Files.readAllBytes(mockArtifactPath);
        byte[] downloadFile = Files.readAllBytes(saveToPath.resolve("artifact.txt"));
        assertThat(Arrays.equals(originalFile, downloadFile), is(true));

        TestHelper.cleanDirectory(testCache);
    }

    @Test
    void GIVEN_http_connection_error_WHEN_attempt_download_THEN_return_exception() throws Exception {
        AmazonServiceException ase = new AmazonServiceException("Redirect");
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "https://www.amazon.com/artifact.txt");
        ase.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
        ase.setHttpHeaders(headers);
        when(client.getArtifact(any())).thenThrow(ase);

        doReturn(connection).when(downloader).connect(any());
        when(connection.getResponseCode()).thenThrow(IOException.class);

        PackageIdentifier pkgId = new PackageIdentifier("CoolService", new Semver("1.0.0"), "CoolServiceARN");
        assertThrows(IOException.class, () -> downloader.downloadToPath(pkgId, new URI("greengrass:binary"), null));
    }

    @Test
    void GIVEN_filename_in_disposition_WHEN_attempt_resolve_filename_THEN_parse_filename() throws Exception {
        String filename = downloader.extractFilename(new URL("https://www.amazon.com/artifact.txt"),
                "attachment; " + "filename=\"filename.jpg\"");

        assertThat(filename, is("filename.jpg"));
    }

    @Test
    void GIVEN_filename_in_url_WHEN_attempt_resolve_filename_THEN_parse_filename() throws Exception {
        String filename =
                downloader.extractFilename(new URL("https://www.amazon.com/artifact.txt?key=value"), "attachment");

        assertThat(filename, is("artifact.txt"));
    }

}