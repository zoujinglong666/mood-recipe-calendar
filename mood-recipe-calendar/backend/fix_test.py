import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\test\java\com\moodrecipe\backend\service\DailyMenuServiceTest.java')
c = f.read_text(encoding='utf-8')

c = c.replace(
    'import com.moodrecipe.backend.repository.RecipeInteractionRepository;\nimport com.moodrecipe.backend.repository.RecipeRepository;',
    'import com.moodrecipe.backend.repository.RecipeInteractionRepository;\nimport com.moodrecipe.backend.repository.RecipeFavoriteRepository;\nimport com.moodrecipe.backend.repository.RecipeRepository;'
)

c = c.replace(
    'new AllergenNormalizationService(null, new ObjectMapper()), new RecipePool(recipes));',
    'new AllergenNormalizationService(null, new ObjectMapper()), new RecipePool(recipes), mock(RecipeFavoriteRepository.class));'
)

f.write_text(c, encoding='utf-8')
print('Test fixed')
