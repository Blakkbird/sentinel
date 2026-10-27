package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.api.BadRequestException;
import com.runtimeerror.sentinel.lab.ReplayReport;
import com.runtimeerror.sentinel.lab.RuleLab;
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
import org.springframework.web.bind.annotation.RequestParam;
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
 *   POST /api/rule-sets/{version}/submit    send a draft for approval: {"revision": 3}
 *   POST /api/rule-sets/{version}/withdraw  take it back to change it
 *   POST /api/rule-sets/{version}/approve   admins: approve and publish: {"revision": 3, "note": "..."}
 *   POST /api/rule-sets/{version}/reject    admins: {"revision": 3, "note": "why"}, the note is required
 *   POST /api/rule-sets/{version}/activate  admins: make a published version live again (rollback)
 *   GET  /api/rule-sets/{version}/replay    Rule Lab: this version against the live rules, on the
 *                                           last N decisions (?decisions=1000, at most 5000)
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

    /** For submit, approve, reject and activate: the revision the person was looking at, and a note. */
    public record Review(Integer revision, String note) {
    }

    private final RuleSetViews views;
    private final RuleSetService rules;
    private final RuleLab lab;

    public RuleSetController(RuleSetViews views, RuleSetService rules, RuleLab lab) {
        this.views = views;
        this.rules = rules;
        this.lab = lab;
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

    @PostMapping("/{version}/submit")
    public RuleSetViews.Detail submit(@PathVariable int version, @RequestBody(required = false) Review body,
                                      Authentication authentication) {
        Actor actor = Actor.from(authentication);
        rules.submit(version, revision(body), actor);
        return views.detail(version, actor);
    }

    @PostMapping("/{version}/withdraw")
    public RuleSetViews.Detail withdraw(@PathVariable int version, Authentication authentication) {
        Actor actor = Actor.from(authentication);
        rules.withdraw(version, actor);
        return views.detail(version, actor);
    }

    @PostMapping("/{version}/approve")
    public RuleSetViews.Detail approve(@PathVariable int version, @RequestBody(required = false) Review body,
                                       Authentication authentication) {
        Actor actor = Actor.from(authentication);
        rules.approve(version, revision(body), body.note(), actor);
        return views.detail(version, actor);
    }

    @PostMapping("/{version}/reject")
    public RuleSetViews.Detail reject(@PathVariable int version, @RequestBody(required = false) Review body,
                                      Authentication authentication) {
        int revision = revision(body);
        if (body.note() == null || body.note().isBlank()) {
            throw new BadRequestException("Say why you are rejecting it, as \"note\", so the author knows what to change.");
        }
        Actor actor = Actor.from(authentication);
        rules.reject(version, revision, body.note(), actor);
        return views.detail(version, actor);
    }

    @PostMapping("/{version}/activate")
    public RuleSetViews.Detail activate(@PathVariable int version, @RequestBody(required = false) Review body,
                                        Authentication authentication) {
        Actor actor = Actor.from(authentication);
        rules.activate(version, body == null ? null : body.note(), actor);
        return views.detail(version, actor);
    }

    private static int revision(Review body) {
        if (body == null || body.revision() == null) {
            throw new BadRequestException("Send the revision you were looking at, as \"revision\".");
        }
        return body.revision();
    }

    /** Read-only, so a GET: it changes nothing, and the result can be bookmarked or refreshed. */
    @GetMapping("/{version}/replay")
    public ReplayReport replay(@PathVariable int version,
                               @RequestParam(defaultValue = "" + RuleLab.DEFAULT_DECISIONS) int decisions) {
        return lab.replay(version, decisions);
    }
}
