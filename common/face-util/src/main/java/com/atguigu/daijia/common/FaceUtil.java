package com.atguigu.daijia.common;

import com.atguigu.daijia.common.model.AddFaceForm;
import com.atguigu.daijia.common.model.FaceLibrary;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * FaceUtil
 *
 * @author Samoy
 * @date 2024/8/7
 */
public class FaceUtil {

    /**
     * 向人脸库中添加人脸照片，如果没有人脸库则会自动创建
     *
     * @param base64Image 图片base64编码
     * @param subjectId   人脸库id
     * @return 人脸id
     */
    public static FaceLibrary addFaceLibrary(String base64Image, String subjectId) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", FaceConstant.FACE_RECOGNITION_API_KEY);
        AddFaceForm addFaceForm = new AddFaceForm();
        // 分割base64的值，只拿base64编码部分
        String[] base64ImageArray = base64Image.split(",");
        addFaceForm.setFile(base64ImageArray[base64ImageArray.length - 1]);
        HttpEntity<AddFaceForm> httpEntity = new HttpEntity<>(addFaceForm, headers);
        ResponseEntity<FaceLibrary> resp = restTemplate
                .postForEntity(FaceConstant.FACE_RECOGNITION_END_POINT
                        + "/faces?subject=" + subjectId, httpEntity, FaceLibrary.class);
        if (resp.getStatusCode().isError()) {
            throw new RuntimeException("添加人脸失败");
        }
        return resp.getBody();
    }
}
