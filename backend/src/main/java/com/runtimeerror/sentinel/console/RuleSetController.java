package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.BadRequestException;
import com.runtimeerror.sentinel.rules.RuleSetService;
import com.runtimeerror.sentinel.rules.StoredRuleSet;
import com.runtimeerror.sentinel.security.Actor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Rule set versions, for the console. Signed-in users only (SecurityConfig).
 *
 *   GET  /api/rule-sets                     every version, newest first, and which one is live
 *   GET  /api/rule-sets/{version}           one version, with what the signed-in person may do to it
 *   POST /api/rule-sets                     start a draft: {"from": 2, "title": "..."}, both optional
 *   PUT  /api/rule-sets/{version}           save a draft: {"revision": 1, "title": "...", "rules": [...]}
 *   POST /api/rule-sets/{version}/discard   throw a draft away
 *
 * Spring passes the signed-in person in as "authentication". It becomes an Actor at once, so
 * nothing past this class needs to know about Spring Security.
 */
@RestController
@RequestMapping("/api/rule-sets")
public class RuleSetController {

    /** Both optional: copy the live version, with a title made up from its number. */
    public record NewDraft(Integer from, String title) {
    }

    /**
     * "rules" is left as plain JSON (lists and maps) on purpose. RuleSetJson reads it and says
     * exactly what is wrong and where, which Jackson's own errors would not.
     */
    public record SaveDraft(Integer revision, String title, Object rules) {
    }

    private final RuleSetViews views;
    private final RuleSetService rules;

    public RuleSetController(RuleSetViews views, RuleSetService rules) {
        this.views = views;
        this.rules = rules;
    }

    @GetMapping
    public RuleSetViews.Listing list() {
        return views.list();
    }

    @GetMapping("/{version}")
    public RuleSetViews.Detail detail(@PathVariable int version, Authentication authentication) {
        return views.detail(version, Actor.from(authentication));
    }

    @PostMapping
    public ResponseEntity<RuleSetViews.Detail> create(@RequestBody(required = false) NewDraft body,
                                                      Authentication authentication) {
        Actor actor = Actor.from(authentication);
        NewDraft request = body == null ? new NewDraft(null, null) : body;
        StoredRuleSet draft = rules.createDraft(request.from(), request.title(), actor);
        // 201 Created, with the new version's address in the Location header.
        return ResponseEntity.created(URI.create("/api/rule-sets/" + draft.version()))
                .body(views.detail(draft.version(), actor));
    }

    @PutMapping("/{version}")
    public RuleSetViews.Detail save(@PathVariable int version, @RequestBody SaveDraft body,
                                    Authentication authentication) {
        if (body.revision() == null) {
            throw new BadRequestException("Send the revision you started editing from, as \"revision\".");
        }
        Actor actor = Actor.from(authentication);
        rules.saveDraft(version, body.revision(), body.title(), body.rules(), actor);
        return views.detail(version, actor);
    }

    @PostMapping("/{version}/discard")
    public RuleSetViews.Detail discard(@PathVariable int version, Authentication authentication) {
        Actor actor = Actor.from(authentication);
        rules.discard(version, actor);
        return views.detail(version, actor);
    }
}
