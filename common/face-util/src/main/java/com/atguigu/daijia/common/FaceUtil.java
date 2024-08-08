package com.atguigu.daijia.common;

import com.atguigu.daijia.common.model.AddFaceForm;
import com.atguigu.daijia.common.model.FaceExample;
import com.atguigu.daijia.common.model.Subject;
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
     * 创建Subject(人脸库)
     *
     * @return 人脸库名称，即某人姓名
     */
    public static Subject createSubject() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", FaceConstant.FACE_RECOGNITION_API_KEY);
        ResponseEntity<Subject> resp = restTemplate
                .postForEntity(FaceConstant.FACE_RECOGNITION_END_POINT + "/subjects", headers, Subject.class);
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("创建人脸库失败");
        }
        return resp.getBody();
    }

    /**
     * 向人脸库中添加人脸照片
     *
     * @param base64Image 图片base64编码
     * @param subjectId   人脸库id
     * @return 人脸id
     */
    public static FaceExample addFace(String base64Image, String subjectId) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", FaceConstant.FACE_RECOGNITION_API_KEY);
        HttpEntity<AddFaceForm> httpEntity = new HttpEntity<>(new AddFaceForm(base64Image), headers);
        ResponseEntity<FaceExample> resp = restTemplate
                .postForEntity(FaceConstant.FACE_RECOGNITION_END_POINT
                        + "/faces?subject=" + subjectId, httpEntity, FaceExample.class);
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("添加人脸失败");
        }
        return resp.getBody();
    }
}
