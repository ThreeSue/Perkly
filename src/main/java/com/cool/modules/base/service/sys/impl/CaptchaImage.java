package com.cool.modules.base.service.sys.impl;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为各端生成透明背景的四位彩色描边静态验证码
 * @Version: v1.1
 * @Date: 2026-11-13
 * @Time: 00:17:10
 */
public final class CaptchaImage {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Color[] COLORS = {
        new Color(37, 99, 200), new Color(8, 145, 165),
        new Color(196, 101, 19), new Color(157, 73, 160), new Color(30, 147, 100)
    };

    private CaptchaImage() {}

    public static String code() {
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        return code.toString();
    }

    public static String render(String code, int width, int height) {
        // Render at twice the display resolution to keep outlines crisp on high-DPI screens.
        BufferedImage image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(2, 2);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(height * .72f));
            float cell = (width - 16f) / code.length();
            for (int i = 0; i < code.length(); i++) {
                Shape glyph = font.createGlyphVector(g.getFontRenderContext(), String.valueOf(code.charAt(i))).getOutline();
                var bounds = glyph.getBounds2D();
                AffineTransform placement = new AffineTransform();
                placement.translate(8 + cell * (i + .5), height * .52 + RANDOM.nextInt(5) - 2);
                placement.rotate((RANDOM.nextDouble() - .5) * .32);
                placement.translate(-bounds.getCenterX(), -bounds.getCenterY());
                Shape outline = placement.createTransformedShape(glyph);
                Color color = COLORS[(i + RANDOM.nextInt(COLORS.length)) % COLORS.length];
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 24));
                g.fill(outline);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(outline);
            }
            g.setStroke(new BasicStroke(.7f));
            for (int i = 0; i < 2; i++) {
                Color color = COLORS[RANDOM.nextInt(COLORS.length)];
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 105));
                g.draw(new CubicCurve2D.Float(3, RANDOM.nextInt(height), width * .3f,
                    RANDOM.nextInt(height), width * .7f, RANDOM.nextInt(height), width - 3, RANDOM.nextInt(height)));
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("验证码图片生成失败", exception);
        }
    }
}
