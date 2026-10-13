package learn.java.billingsoftware.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.io.ItemRequest;
import learn.java.billingsoftware.io.ItemResponse;
import learn.java.billingsoftware.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;
    private final ObjectMapper objectMapper;

    @PostMapping("/admin/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse addItem(@RequestPart("item") String itemString,
                                @RequestPart(value = "file", required = false) MultipartFile file) {
        ItemRequest itemRequest = null;
        try {
            itemRequest = objectMapper.readValue(itemString, ItemRequest.class);
            return itemService.add(itemRequest, file);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PutMapping(value = "/admin/items/{itemId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse updateItemMultipart(@PathVariable("itemId") String itemId,
                                            @RequestPart("item") String itemString,
                                            @RequestPart(value = "file", required = false) MultipartFile file) {
        try {
            ItemRequest itemRequest = objectMapper.readValue(itemString, ItemRequest.class);
            return itemService.updateItem(itemId, itemRequest, file);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PutMapping(value = "/admin/items/{itemId}", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ItemResponse updateItemJson(@PathVariable("itemId") String itemId,
                                       @RequestBody ItemRequest itemRequest) {
        try {
            return itemService.updateItem(itemId, itemRequest, null);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/items")
    public List<ItemResponse> readItems() {
        return itemService.fetchItems();
    }

    @GetMapping("/items/{itemId}")
    public ItemResponse readItem(@PathVariable("itemId") String itemId) {
        return itemService.fetchItem(itemId);
    }

    @DeleteMapping("/admin/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable("itemId") String itemId) {
        try {
            itemService.deleteItem(itemId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
