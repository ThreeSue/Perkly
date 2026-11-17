package com.cool.modules.searchrisk;
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("risk_word_state")
public class RiskWordState {
 @com.mybatisflex.annotation.Id private Long id;
 private Long revision;private Boolean initialized;private String checksum;
}
