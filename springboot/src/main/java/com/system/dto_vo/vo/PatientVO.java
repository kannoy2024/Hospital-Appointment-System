package com.system.dto_vo.vo;


import com.system.common.PrivacyUtils;
import com.system.entity.Patient;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;

@Data
public class PatientVO {

    private Long id;
    private String name;
    private String phone;
    private String idCard;
    private Integer gender;
    private LocalDate birthDate;
    private Integer nation;
    private String address;
    private Integer marital;
    private Integer isDefault;

    public static PatientVO fromEntity(Patient patient) {
        PatientVO vo = new PatientVO();
        BeanUtils.copyProperties(patient, vo);
        return vo;
    }

    public static PatientVO fromEntityMasked(Patient patient) {
        PatientVO vo = fromEntity(patient);
        vo.setPhone(PrivacyUtils.maskPhone(vo.getPhone()));
        vo.setIdCard(PrivacyUtils.maskIdCard(vo.getIdCard()));
        return vo;
    }
}
