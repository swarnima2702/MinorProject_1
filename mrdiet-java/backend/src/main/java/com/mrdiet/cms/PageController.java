package com.mrdiet.cms;

import com.mrdiet.common.Db;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Public CMS pages (privacy policy, terms …). Was admin/cms.php GET — editing lives in AdminController. */
@RestController
public class PageController {

    private final Db db;

    public PageController(Db db) {
        this.db = db;
    }

    @GetMapping("/api/pages")
    public List<Map<String, Object>> list() {
        return db.list("SELECT slug, title FROM pages ORDER BY title");
    }

    /** Returns the page or JSON null (PHP returned false/null for a missing slug). */
    @GetMapping("/api/pages/{slug}")
    public Map<String, Object> get(@PathVariable String slug) {
        return db.one("SELECT * FROM pages WHERE slug = ?", slug);
    }
}
