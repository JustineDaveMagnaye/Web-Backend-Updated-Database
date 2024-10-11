package com.rocs.osd.domain.csReport;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
/**
 * Represents the community service report (CsReport) entity.
 */
@Entity
@Data
public class CsReport implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;
    private Date dateOfCs;
    private Timestamp timeIn;
    private Timestamp timeOut;
    @Column(length = 4, nullable = false)
    private int hoursCompleted;
    @Column(length = 32, nullable = false)
    private String natureOfWork;
    @Column(length = 16, nullable = false)
    private String status;
    @Column(length = 64, nullable = false)
    private String remarks;
}
