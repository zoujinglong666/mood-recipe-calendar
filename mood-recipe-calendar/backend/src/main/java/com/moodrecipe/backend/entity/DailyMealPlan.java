package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data @Entity @Table(name="daily_meal_plans", uniqueConstraints=@UniqueConstraint(columnNames={"openid","plan_date"}))
public class DailyMealPlan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=64) private String openid;
 @Column(name="plan_date",nullable=false) private LocalDate planDate;
 @Column(name="plan_json",nullable=false,columnDefinition="TEXT") private String planJson;
}
