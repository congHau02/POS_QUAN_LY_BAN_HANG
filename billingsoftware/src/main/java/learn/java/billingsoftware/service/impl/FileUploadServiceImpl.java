package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    @Value("${aws.bucket.name}")
    private String bucketName;
    private final S3Client s3Client;

    @Override
    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp tải lên không được để trống");
        }
        String originalFilename = file.getOriginalFilename();
        String filenameExtension = "";
        if (originalFilename != null && originalFilename.lastIndexOf(".") >= 0) {
            filenameExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        String key = filenameExtension.isEmpty() ? UUID.randomUUID().toString() : UUID.randomUUID().toString() + "." + filenameExtension;
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .acl("public-read")
                    .contentType(file.getContentType())
                    .build();
            PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            if (response.sdkHttpResponse().isSuccessful()) {
                return "https://" + bucketName + ".s3.amazonaws.com/" + key;
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Tải lên tệp S3 thất bại");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi đọc dữ liệu tệp tải lên: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteFile(String imgUrl) {
        if (imgUrl == null || imgUrl.isBlank()) {
            return false;
        }
        String filename = imgUrl.substring(imgUrl.lastIndexOf("/") + 1);
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(filename)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
        return true;
    }
}
