package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.io.CategoryRequest;
import learn.java.billingsoftware.io.CategoryResponse;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.service.CategoryService;
import learn.java.billingsoftware.service.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CategoryUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @MockitoBean
    private FileUploadService fileUploadService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(fileUploadService.uploadFile(any())).thenReturn("https://test-s3.com/mock-image.png");
        when(fileUploadService.deleteFile(any())).thenReturn(true);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Admin can update category via JSON PUT and emits UPDATE activity log")
    void testUpdateCategoryJson() throws Exception {
        CategoryRequest initial = CategoryRequest.builder()
                .name("Beverages " + UUID.randomUUID().toString().substring(0, 6))
                .description("Initial description")
                .bgColor("#1E40AF")
                .build();
        CategoryResponse created = categoryService.create(initial);
        String categoryId = created.getCategoryId();

        CategoryRequest updateRequest = CategoryRequest.builder()
                .name("Beverages Updated")
                .description("Updated description")
                .bgColor("#059669")
                .build();

        mockMvc.perform(put("/admin/categories/" + categoryId)
                .with(user("staff@billing.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/admin/categories/" + categoryId)
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.name").value("Beverages Updated"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.bgColor").value("#059669"));

        boolean logged = activityLogRepository.findAll().stream().anyMatch(l ->
                "UPDATE".equalsIgnoreCase(l.getAction()) &&
                "CATEGORY".equalsIgnoreCase(l.getEntityType()) &&
                categoryId.equals(l.getEntityId()) &&
                "admin@billing.com".equals(l.getUserEmail()));
        assertThat(logged).isTrue();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Admin can update category via multipart PUT with file and retains existing image when omitted")
    void testUpdateCategoryMultipart() throws Exception {
        CategoryRequest initial = CategoryRequest.builder()
                .name("Pastry " + UUID.randomUUID().toString().substring(0, 6))
                .description("Initial Pastry")
                .bgColor("#F59E0B")
                .build();
        MockMultipartFile file1 = new MockMultipartFile("file", "cake.jpg", "image/jpeg", "cake-image-bytes".getBytes());
        CategoryResponse created = categoryService.add(initial, file1);
        String categoryId = created.getCategoryId();
        String initialImgUrl = created.getImgUrl();
        assertThat(initialImgUrl).isNotNull();

        CategoryRequest updateWithoutFile = CategoryRequest.builder()
                .name("Pastry Renamed")
                .description("Updated Pastry Description")
                .bgColor("#EF4444")
                .build();

        MockMultipartFile categoryPart = new MockMultipartFile(
                "category",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(updateWithoutFile)
        );

        mockMvc.perform(multipart(HttpMethod.PUT, "/admin/categories/" + categoryId)
                .file(categoryPart)
                .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pastry Renamed"))
                .andExpect(jsonPath("$.imgUrl").value(initialImgUrl));

        MockMultipartFile file2 = new MockMultipartFile("file", "croissant.jpg", "image/jpeg", "croissant-bytes".getBytes());
        mockMvc.perform(multipart(HttpMethod.PUT, "/admin/categories/" + categoryId)
                .file(categoryPart)
                .file(file2)
                .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pastry Renamed"));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Updating non-existent category returns 404")
    void testUpdateNonExistentCategory() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Non-existent")
                .build();

        mockMvc.perform(put("/admin/categories/" + UUID.randomUUID())
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
