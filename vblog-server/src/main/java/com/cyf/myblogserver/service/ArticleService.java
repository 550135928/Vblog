package com.cyf.myblogserver.service;

import com.cyf.myblogserver.entity.Category;
import com.cyf.myblogserver.entity.Tag;
import com.cyf.myblogserver.entity.User;
import com.cyf.myblogserver.exception.CommonException;
import com.cyf.myblogserver.exception.Error;
import com.cyf.myblogserver.repository.CategoryRepository;
import com.cyf.myblogserver.repository.TagRepository;
import com.cyf.myblogserver.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger; // 调用slf4j接口
import org.slf4j.LoggerFactory;
import com.cyf.myblogserver.entity.Article;
import com.cyf.myblogserver.repository.ArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ArticleService {

    ArticleRepository articleRepository;
    TagRepository tagRepository;
    CategoryRepository categoryRepository;
    UserRepository userRepository;

    public ArticleService(ArticleRepository articleRepository, TagRepository tagRepository, CategoryRepository categoryRepository, UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save given article to database, while updating the tag and category
     * @param article The article need to be saved
     * @return Id if saved article
     */
    public Long saveArticle(Article article) throws CommonException {
        article = processTagAndCategory(article);
        // save the article and return its id;
        Article article1 = articleRepository.save(article);
        log.info("Article saved with id {}", article1.getId());
        return article1.getId();
    }

    /**
     * Edit given article
     * @param article Edited version of article
     * @throws CommonException
     */
    public void editArticle(Article article) throws CommonException {
        Article oldArticle;
        try {
            oldArticle = articleRepository.findById(article.getId()).get();
        } catch (NoSuchElementException e){
            throw new CommonException(Error.ARTICLE_NOT_FOUND.getCode(), 404, Error.ARTICLE_NOT_FOUND.getMsg());
        }

        if(oldArticle.getUser().getId()!=article.getUser().getId()){
            throw new CommonException(Error.PERMISSION_DENIED.getCode(), 404, Error.PERMISSION_DENIED.getMsg());
        }

        article = processTagAndCategory(article);
        oldArticle.setTags(article.getTags());
        oldArticle.setCategory(article.getCategory());
        oldArticle.setMdContent(article.getMdContent());
        oldArticle.setTitle(article.getTitle());
        oldArticle.setState(article.getState());
        articleRepository.save(oldArticle);
    }

    public void deleteArticle(Long id, Long userId) throws CommonException {
        try {
            Article article = articleRepository.findById(id).get();
            if(article.getUser().getId() != userId){
                throw new CommonException(Error.PERMISSION_DENIED.getCode(), 403, Error.PERMISSION_DENIED.getMsg());
            }
            articleRepository.deleteById(id);
        } catch (NoSuchElementException e){
            throw new CommonException(Error.ARTICLE_NOT_FOUND.getCode(), 404, Error.ARTICLE_NOT_FOUND.getMsg());
        }
    }

    /**
     * Get Articles ordered by created date with limit and page
     * @param page The page number you want to retrieve
     * @param limit Number of articles within a page
     * @param username The name of the user that the article belongs to
     * @return  The data containing all the article
     */
    public Page<Article> getArticlesByUserName(Integer page, Integer limit, String username) throws CommonException {
        Sort sort = Sort.by(Sort.Direction.DESC, "createDate");
        User user = userRepository.findByUsername(username);
        if(user == null){
            throw new CommonException(Error.USER_NOT_FOUNT.getCode(), 404, Error.USER_NOT_FOUNT.getMsg());
        }

        Pageable pageable = PageRequest.of(page, limit, sort);
        return articleRepository.findByStateAndUserId(pageable, Article.PUBLISHED, user.getId());
    }

    /**
     * Get an particular article with given id
     * @param id Article id
     * @return  Article entity
     * @throws CommonException
     */

    public Article getArticle(Long id) throws CommonException {
        try {
            Article article = articleRepository.findById(id).get();
            return article;
        }catch (NoSuchElementException e){
            throw new CommonException(Error.ARTICLE_NOT_FOUND.getCode(),404,Error.ARTICLE_NOT_FOUND.getMsg());
        }
    }

    private Article processTagAndCategory(Article article) throws CommonException {
        User user;
        try {
             user = userRepository.findById(article.getUser().getId()).get();
        }catch (NoSuchElementException e){
            throw new CommonException(Error.USER_NOT_FOUNT.getCode(), 404, Error.USER_NOT_FOUNT.getMsg());
        }
        if(article.getTags() != null) {
            log.info("Article with tags {}", article.getTags());
            Map<String, Tag> map = new HashMap<>();
            for (Tag tag : article.getTags()) {
                if (!map.containsKey(tag.getTagName())) {
                    Tag existingTag = tagRepository.findByTagNameAndUserId(tag.getTagName(), user.getId());

                    if (existingTag == null) {
                        tag.setUser(user);
                        // If the tag doesn't exist, create the tag
                        tagRepository.save(tag);
                    } else {

                        // If exist, replace the id and increase the tag count int the db
                        tag.setId(existingTag.getId());
                    }
                    map.put(tag.getTagName(), tag);
                }
            }
            article.setTags(new ArrayList<>(map.values()));
        }

        if(article.getCategory()!=null) {
            // Check the article's category
            log.info("Article with category {}", article.getCategory());

            // Check whether the category exist
            Category existingCategory = categoryRepository.findByCategoryNameAndUserId(article.getCategory().getCategoryName(), user.getId());
            if (existingCategory == null) {
                article.getCategory().setUser(user);
                // If not, then create the tag;
                categoryRepository.save(article.getCategory());
            } else {

                // If exist, replace the id and increase the category count int the db
                article.getCategory().setId(existingCategory.getId());
            }
        }
        return article;
    }
}
