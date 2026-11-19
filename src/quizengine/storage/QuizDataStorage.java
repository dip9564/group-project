package quizengine.storage;

import quizengine.exception.QuizException;
import quizengine.model.LoadResult;
import quizengine.model.Question;
import quizengine.model.Score;

/**
 * Storage abstraction interface defining persistent flat-file operations for
 * the quiz competition engine.
 */
public interface QuizDataStorage {

    LoadResult<Question> loadQuestions() throws QuizException;

    LoadResult<Score> loadScores() throws QuizException;

    void appendScore(Score score) throws QuizException;

    String generateNextAttemptId() throws QuizException;
}
