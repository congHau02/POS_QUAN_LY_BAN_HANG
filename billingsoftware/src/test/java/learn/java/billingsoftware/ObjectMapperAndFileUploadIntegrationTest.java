package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.service.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
public class ObjectMapperAndFileUploadIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileUploadService fileUploadService;

    @MockitoBean
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        SdkHttpResponse sdkHttp = (SdkHttpResponse) SdkHttpResponse.builder().statusCode(200).build();
        PutObjectResponse putResponse = (PutObjectResponse) PutObjectResponse.builder().sdkHttpResponse(sdkHttp).build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putResponse);
    }

    @Test
    void testObjectMapperBeanIsAvailable() {
        assertNotNull(objectMapper);
    }

    @Test
    void testUploadFileWithNullOriginalFilenameDoesNotThrowNpe() {
        MockMultipartFile file = new MockMultipartFile("file", null, "image/png", new byte[]{1, 2, 3});
        String url = fileUploadService.uploadFile(file);
        assertNotNull(url);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertFalse(captor.getValue().key().endsWith(".null"));
        assertFalse(captor.getValue().key().endsWith("."));
    }

    @Test
    void testUploadFileWithoutExtensionDoesNotProduceCorruptedKey() {
        MockMultipartFile file = new MockMultipartFile("file", "filenameWithNoDot", "image/png", new byte[]{1, 2, 3});
        String url = fileUploadService.uploadFile(file);
        assertNotNull(url);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertFalse(captor.getValue().key().endsWith(".filenameWithNoDot"));
    }
}
