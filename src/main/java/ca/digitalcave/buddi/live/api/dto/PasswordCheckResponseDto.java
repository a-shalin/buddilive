package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordCheckResponseDto {

	private final int score;
	private final boolean passed;

	private Boolean length;
	private Integer minLength;
	private Boolean strength;
	private Integer minStrength;
	private Boolean variance;
	private Integer minVariance;
	private Boolean classes;
	private Integer minClasses;
	private Boolean dictionary;
	private Boolean pattern;
	private Boolean history;
	private Boolean custom;

	public PasswordCheckResponseDto(final int score, final boolean passed) {
		this.score = score;
		this.passed = passed;
	}

	public int getScore() {
		return score;
	}

	public boolean isPassed() {
		return passed;
	}

	public Boolean getLength() {
		return length;
	}

	public void setLength(final Boolean length) {
		this.length = length;
	}

	public Integer getMinLength() {
		return minLength;
	}

	public void setMinLength(final Integer minLength) {
		this.minLength = minLength;
	}

	public Boolean getStrength() {
		return strength;
	}

	public void setStrength(final Boolean strength) {
		this.strength = strength;
	}

	public Integer getMinStrength() {
		return minStrength;
	}

	public void setMinStrength(final Integer minStrength) {
		this.minStrength = minStrength;
	}

	public Boolean getVariance() {
		return variance;
	}

	public void setVariance(final Boolean variance) {
		this.variance = variance;
	}

	public Integer getMinVariance() {
		return minVariance;
	}

	public void setMinVariance(final Integer minVariance) {
		this.minVariance = minVariance;
	}

	public Boolean getClasses() {
		return classes;
	}

	public void setClasses(final Boolean classes) {
		this.classes = classes;
	}

	public Integer getMinClasses() {
		return minClasses;
	}

	public void setMinClasses(final Integer minClasses) {
		this.minClasses = minClasses;
	}

	public Boolean getDictionary() {
		return dictionary;
	}

	public void setDictionary(final Boolean dictionary) {
		this.dictionary = dictionary;
	}

	public Boolean getPattern() {
		return pattern;
	}

	public void setPattern(final Boolean pattern) {
		this.pattern = pattern;
	}

	public Boolean getHistory() {
		return history;
	}

	public void setHistory(final Boolean history) {
		this.history = history;
	}

	public Boolean getCustom() {
		return custom;
	}

	public void setCustom(final Boolean custom) {
		this.custom = custom;
	}
}
