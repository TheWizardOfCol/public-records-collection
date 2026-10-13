package com.publicrecords.collection.cryptid;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Book {
  @JsonProperty public final String id;
  @JsonProperty public final String title;
  @JsonProperty public final String author;
  @JsonProperty public final String year;
  @JsonProperty public final String url;
  public Book(String id, String title, String author, String year, String url) {
    this.id=id; this.title=title; this.author=author; this.year=year; this.url=url;
  }
}
