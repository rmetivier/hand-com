package fr.handcom.post;

import fr.handcom.post.PostDtos.PostDto;
import fr.handcom.post.PostDtos.PostRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @GetMapping
    List<PostDto> list() {
        return service.list().stream().map(PostDto::of).toList();
    }

    @GetMapping("/{id}")
    PostDto get(@PathVariable Long id) {
        return PostDto.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PostDto create(@Valid @RequestBody PostRequest request) {
        return PostDto.of(service.create(request));
    }

    @PutMapping("/{id}")
    PostDto update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        return PostDto.of(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish-now")
    PostDto publishNow(@PathVariable Long id) {
        return PostDto.of(service.publishNow(id));
    }
}
