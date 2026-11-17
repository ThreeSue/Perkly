package com.cool.modules.searchrisk;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.searchrisk.mapper.SearchWordMapper;
import com.cool.modules.support.service.SupportAccess;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营端的风险词库分页查询和维护接口
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:27:31
 */
@RestController
@RequestMapping({"/admin/search-risk", "/admin/content-risk"})
@lombok.RequiredArgsConstructor
public class RiskWordController {

    private final SearchWordMapper words;
    private final RiskWordService service;
    private final ContentRiskDictionary dictionary;
    private final SupportAccess access;
    private final SearchRiskService detection;

    public record WordInput(Long id, String word, String mode, Boolean enabled) {}
    public record WordId(Long id) {}
    public record ImportInput(String version, String requestId) {}
    public record WordPage(List<SearchWord> list, long total, int page, int size) {}
    public record RuleInput(String text,String scene) {}

    @PostMapping("/words/test")
    public R<SearchRiskService.RuleResult> test(@RequestBody RuleInput input) {
        access.platform("search:risk:manage");
        if(input==null)throw new CoolException("缺少测试内容",400);
        return R.ok(detection.testRule(input.text(),input.scene()));
    }

    @GetMapping("/words/detail")
    public R<SearchWord> detail(@RequestParam(required=false) Long id,@RequestParam(required=false) String word) {
        access.platform("search:risk:view");
        if(id!=null){
            var row=words.selectOneById(id);
            if(row!=null)return R.ok(row);
        }else if(word!=null && !word.isBlank() && word.length()<=100){
            String normalized=SearchRiskService.normalize(word);
            var matches=words.selectListByQuery(activeWords().eq(SearchWord::getWord,word)).stream()
                .filter(row->normalized.equals(SearchRiskService.normalize(row.getWord()))).toList();
            if(matches.size()==1)return R.ok(matches.get(0));
        }
        throw new CoolException("没有可关联的管理词条",404);
    }

    @GetMapping("/word-sources")
    public R<?> sources() {
        access.platform("search:risk:view");
        return R.ok(dictionary.sources());
    }

    @GetMapping("/words")
    public R<?> words() {
        access.platform("search:risk:view");
        return R.ok(words.selectListByQuery(activeWords().orderBy(SearchWord::getId, false)));
    }

    @GetMapping("/words/page")
    public R<?> page(@RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "20") int size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Boolean enabled,
                     @RequestParam(required = false) String source) {
        access.platform("search:risk:view");
        SupportAccess.page(page, size);
        var query = activeWords().orderBy(SearchWord::getId, false);
        if (keyword != null && !keyword.isBlank()) query.like(SearchWord::getWord, keyword.trim());
        if (enabled != null) query.eq(SearchWord::getEnabled, enabled);
        if (source != null && !source.isBlank()) query.eq(SearchWord::getSource, source.trim());
        var result = words.paginate(page, size, query);
        return R.ok(new WordPage(result.getRecords(), result.getTotalRow(), page, size));
    }

    @PostMapping("/words/save")
    public R<?> save(@RequestBody WordInput input) {
        access.platform("search:risk:manage");
        if (input == null) throw new CoolException("缺少词条参数", 400);
        return R.ok(service.save(input.id(), input.word(), input.mode(), input.enabled()));
    }

    @PostMapping("/words/delete")
    public R<?> delete(@RequestBody WordId input) {
        access.platform("search:risk:manage");
        service.delete(input == null ? null : input.id());
        return R.ok();
    }

    @PostMapping("/words/import-houbb")
    public R<?> importHoubb(@RequestBody ImportInput input) {
        access.platform("search:risk:manage");
        if (input == null) throw new CoolException("缺少词库导入参数", 400);
        return R.ok(service.importHoubb(input.version(), input.requestId()));
    }

    private QueryWrapper activeWords() {
        return QueryWrapper.create().eq(SearchWord::getDeleted, false);
    }
}
