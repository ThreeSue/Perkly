package com.cool.modules.space.controller;

import com.cool.core.request.R;
import com.cool.modules.space.service.MerchantMaterialService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供商家和门店工作区共用的素材库接口
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 10:51:03
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/governance/materials")
public class MerchantMaterialController {
    private final MerchantMaterialService materials;

    public record IdInput(Long id) {}
    public record MoveInput(Long id, Long categoryId) {}
    public record CategoryInput(Long id, String name) {}

    @GetMapping("/page")
    public R<?> page(@RequestAttribute("merchantUserId") Long actor,
                     @RequestParam(defaultValue = "1") int pageNumber,
                     @RequestParam(defaultValue = "20") int pageSize,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long categoryId,@RequestParam(required=false) String source) {
        return R.ok(materials.page(actor, pageNumber, pageSize, keyword, categoryId,source));
    }

    @GetMapping("/info")
    public R<?> info(@RequestAttribute("merchantUserId") Long actor, @RequestParam Long id) {
        return R.ok(materials.info(actor, id));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<?> upload(@RequestAttribute("merchantUserId") Long actor,
                       @RequestParam(required = false) Long categoryId,
                       @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return R.ok(materials.upload(actor, categoryId, file, request));
    }

    @PostMapping("/move")
    public R<?> move(@RequestAttribute("merchantUserId") Long actor, @RequestBody MoveInput input) {
        materials.move(actor, input.id(), input.categoryId());
        return R.ok();
    }

    @PostMapping("/import-covers")
    public R<?> importCovers(@RequestAttribute("merchantUserId") Long actor) {
        return R.ok(materials.importCovers(actor));
    }

    @PostMapping("/delete")
    public R<?> delete(@RequestAttribute("merchantUserId") Long actor, @RequestBody IdInput input) {
        materials.delete(actor, input.id());
        return R.ok();
    }

    @GetMapping("/categories")
    public R<?> categories(@RequestAttribute("merchantUserId") Long actor) {
        return R.ok(materials.categories(actor));
    }

    @PostMapping("/categories/save")
    public R<?> saveCategory(@RequestAttribute("merchantUserId") Long actor, @RequestBody CategoryInput input) {
        return R.ok(materials.saveCategory(actor, input.id(), input.name()));
    }

    @PostMapping("/categories/delete")
    public R<?> deleteCategory(@RequestAttribute("merchantUserId") Long actor, @RequestBody IdInput input) {
        materials.deleteCategory(actor, input.id());
        return R.ok();
    }
}
