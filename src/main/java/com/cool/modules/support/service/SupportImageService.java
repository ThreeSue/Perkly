package com.cool.modules.support.service;
import com.cool.core.exception.CoolException;
import com.cool.core.file.strategy.LocalFileUploadStrategy;
import com.cool.modules.support.entity.SupportImageEntity;
import com.cool.modules.support.mapper.SupportImageMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 校验图片内容并记录归属以供反馈和聊天引用
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:39
 */
@Service @RequiredArgsConstructor
public class SupportImageService {
    private final SupportImageMapper images;
    private final LocalFileUploadStrategy files;
    public String upload(String kind,Long id,MultipartFile file,HttpServletRequest request) {
        if(file==null || file.isEmpty() || file.getSize()>5*1024*1024)throw new CoolException("请选择不超过5MB的图片");
        String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        if(!name.matches(".*\\.(png|jpe?g|gif)$"))throw new CoolException("仅支持JPG、PNG、GIF");
        String expected=name.endsWith(".png")?"png":name.endsWith(".gif")?"gif":"jpeg";
        try(var input=file.getInputStream();var stream=javax.imageio.ImageIO.createImageInputStream(input)){
            if(stream==null)throw new CoolException("图片内容无效");
            var readers=javax.imageio.ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new CoolException("图片内容无效");
            var reader=readers.next();
            try {
                reader.setInput(stream);
                long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                if(!expected.equalsIgnoreCase(reader.getFormatName()) || pixels<=0 || pixels>40_000_000)throw new CoolException("图片格式或尺寸无效");
            } finally {reader.dispose();}
        } catch(java.io.IOException e){throw new CoolException("图片读取失败");}
        String url=(String)files.upload(new MultipartFile[]{file},request,null);
        var image=new SupportImageEntity();image.setOwnerKind(kind);image.setOwnerId(id);image.setUrl(url);images.insert(image);return url;
    }
    public List<String> validate(String kind,Long id,List<String> urls) {
        if(urls==null)return List.of();
        if(urls.size()>9 || urls.stream().anyMatch(u->u==null || u.length()>1000))throw new CoolException("最多9张有效图片");
        var result=urls.stream().distinct().toList();
        for(String url:result)if(images.selectCountByQuery(QueryWrapper.create().eq(SupportImageEntity::getOwnerKind,kind)
            .eq(SupportImageEntity::getOwnerId,id).eq(SupportImageEntity::getUrl,url))==0)throw new CoolException("图片不属于当前上传账号",403);
        return result;
    }
}
