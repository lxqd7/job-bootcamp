package com.jobbootcamp.marketing.domain;

public class BoardRow {

    private Long userId;
    private String nickname;
    private Long score;
    private Long rankNo;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
    public Long getRankNo() { return rankNo; }
    public void setRankNo(Long rankNo) { this.rankNo = rankNo; }
}