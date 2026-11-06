package com.cool.modules.ai.service;
import com.cool.core.config.LocalFileProperties;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.entity.AiImageTaskEntity;
import com.cool.modules.space.entity.SpaceInfoEntity;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 安全下载厂商结果持久化本地图片并仅从本地归属素材读取参考图
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:46:07
 */
@Service @RequiredArgsConstructor
public class AiFileStore {
    private final LocalFileProperties files;
    public record Stored(String relativePath,String fileId,String url,int size) {}
    private Path journalPath(String task)throws java.io.IOException {
        if(task==null || !task.matches("ai-[a-f0-9]{32}\\.png"))throw new java.io.IOException("Invalid task");
        var directory=Path.of("assets/ai-recovery").toAbsolutePath().normalize();Files.createDirectories(directory);
        return directory.resolve(task+".json");
    }
    public void journal(String task,String result)throws java.io.IOException {
        Path destination=journalPath(task),temporary=Files.createTempFile(destination.getParent(),"result-",".tmp");
        try{Files.writeString(temporary,result,java.nio.charset.StandardCharsets.UTF_8);Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temporary);}
    }
    public String recovery(String task)throws java.io.IOException {
        Path file=journalPath(task);
        if(!Files.isRegularFile(file))return null;
        if(Files.size(file)>16*1024*1024)throw new java.io.IOException("Recovery result too large");
        return Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);
    }
    public void clearJournal(String task)throws java.io.IOException {Files.deleteIfExists(journalPath(task));}
    private Path root()throws java.io.IOException {
        var root=Path.of(files.getAbsoluteUploadFolder()).toAbsolutePath().normalize();Files.createDirectories(root);return root.toRealPath();
    }
    public String reference(SpaceInfoEntity material)throws java.io.IOException {
        if(!"AI".equals(material.getSource()) || material.getFilePath()==null)throw new CoolException("仅能完善本系统已生成并保存的图片");
        var root=root();var image=root.resolve(material.getFilePath()).normalize();
        if(!image.startsWith(root) || !Files.isRegularFile(image) || !image.toRealPath().startsWith(root))throw new CoolException("参考图文件不可用");
        byte[] data=Files.readAllBytes(image);
        if(data.length>5*1024*1024)throw new CoolException("参考图超过5MB");
        return "data:image/png;base64,"+Base64.getEncoder().encodeToString(data);
    }
    public Stored store(AiImageTaskEntity task,String url,String base64)throws Exception {
        byte[] data;
        if(base64!=null && !base64.isBlank()) {
            if(base64.length()>15*1024*1024)throw new CoolException("生成结果超过大小限制");
            data=Base64.getDecoder().decode(base64);
        } else {
            URI uri=URI.create(url);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || uri.getHost()==null
                || !uri.getHost().endsWith(".aliyuncs.com"))throw new CoolException("图片结果地址不属于受信任的云存储");
            for(var address:InetAddress.getAllByName(uri.getHost()))
                if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress())
                    throw new CoolException("图片结果地址不可用");
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
            var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                if(response.statusCode()!=200)throw new CoolException("云端图片暂不可下载，HTTP "+response.statusCode());
                data=stream.readNBytes(10*1024*1024+1);
            }
        }
        if(data.length<100 || data.length>10*1024*1024)throw new CoolException("图片结果大小无效");
        java.awt.image.BufferedImage image;
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            var readers=javax.imageio.ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new CoolException("云端结果不是可识别的图片");
            var reader=readers.next();
            try {
                reader.setInput(input);long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                if(pixels<1 || pixels>16_777_216L)throw new CoolException("生成图片尺寸超出范围");
                image=reader.read(0);
            } finally{reader.dispose();}
        }
        var buffer=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",buffer);
        byte[] png=buffer.toByteArray();
        if(png.length>5*1024*1024)throw new CoolException("生成图片超过素材库5MB限制");
        if(task.getImageKey()==null || !task.getImageKey().matches("ai-[a-f0-9]{32}\\.png"))throw new CoolException("图片保存标识无效");
        String relative="ai/"+task.getImageKey();Path destination=root().resolve(relative);
        Files.createDirectories(destination.getParent());
        Path temporary=Files.createTempFile(destination.getParent(),"ai-",".tmp");
        try {
            Files.write(temporary,png);
            Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
        } finally{Files.deleteIfExists(temporary);}
        String base=files.getBaseUrl();
        if(base==null || base.isBlank())throw new CoolException("未配置本地素材地址");
        return new Stored(relative,task.getImageKey(),base.replaceAll("/+$","")+"/"+relative,png.length);
    }
}
