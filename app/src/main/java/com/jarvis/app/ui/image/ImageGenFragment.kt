package com.jarvis.app.ui.image

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.ai.image.ImageGenerationParams
import com.jarvis.app.ai.image.StableDiffusionEngine
import com.jarvis.app.data.database.AppDatabase
import com.jarvis.app.data.database.GeneratedImageEntity
import com.jarvis.app.databinding.FragmentImageGenBinding
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * ImageGenFragment
 * Pantalla de generación de imágenes con Stable Diffusion local
 * Permite ajustar: prompt, steps, CFG scale, resolución, seed
 */
class ImageGenFragment : Fragment() {

    private var _binding: FragmentImageGenBinding? = null
    private val binding get() = _binding!!
    private val sdEngine = StableDiffusionEngine.getInstance()
    private var generatedBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageGenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        checkModelStatus()
    }

    private fun checkModelStatus() {
        if (!sdEngine.isLoaded()) {
            binding.textViewSdStatus.text = "⚠️ Modelo SD no cargado. Ve a Modelos para descargarlo."
            binding.textViewSdStatus.visibility = View.VISIBLE
            binding.buttonGenerate.isEnabled = false
        } else {
            binding.textViewSdStatus.visibility = View.GONE
            binding.buttonGenerate.isEnabled = true
        }
    }

    private fun setupUI() {
        // Slider de pasos
        binding.sliderSteps.apply {
            valueTo = 50f
            valueFrom = 4f
            value = JarvisConfig.SD_DEFAULT_STEPS.toFloat()
            stepSize = 1f
            addOnChangeListener { _, value, _ ->
                binding.textViewStepsValue.text = value.toInt().toString()
            }
        }
        binding.textViewStepsValue.text = JarvisConfig.SD_DEFAULT_STEPS.toString()

        // Slider CFG scale
        binding.sliderCfg.apply {
            valueFrom = 1f
            valueTo = 15f
            value = JarvisConfig.SD_DEFAULT_CFG_SCALE
            stepSize = 0.5f
            addOnChangeListener { _, value, _ ->
                binding.textViewCfgValue.text = String.format("%.1f", value)
            }
        }
        binding.textViewCfgValue.text = JarvisConfig.SD_DEFAULT_CFG_SCALE.toString()

        // Botón generar
        binding.buttonGenerate.setOnClickListener { generateImage() }

        // Botón guardar
        binding.buttonSaveImage.setOnClickListener { saveImage() }

        // Botón limpiar
        binding.buttonClearPrompt.setOnClickListener {
            binding.editTextPrompt.text?.clear()
            binding.editTextNegPrompt.text?.clear()
        }

        // Sugerencias de prompt rápido
        setupPromptSuggestions()
    }

    private fun setupPromptSuggestions() {
        val suggestions = listOf(
            "Paisaje de montaña al atardecer",
            "Ciudad futurista cyberpunk",
            "Retrato fotorrealista",
            "Ilustración de fantasía",
            "Arte abstracto colorido"
        )

        binding.chipGroupSuggestions.removeAllViews()
        suggestions.forEach { suggestion ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = suggestion
                isClickable = true
                isCheckable = false
                setOnClickListener {
                    binding.editTextPrompt.setText(suggestion)
                }
            }
            binding.chipGroupSuggestions.addView(chip)
        }
    }

    private fun generateImage() {
        val prompt = binding.editTextPrompt.text?.toString()?.trim() ?: ""
        if (prompt.isEmpty()) {
            Toast.makeText(requireContext(), "Escribe un prompt primero", Toast.LENGTH_SHORT).show()
            return
        }

        if (!sdEngine.isLoaded()) {
            Toast.makeText(requireContext(), "Modelo SD no cargado", Toast.LENGTH_SHORT).show()
            return
        }

        val steps = binding.sliderSteps.value.toInt()
        val cfgScale = binding.sliderCfg.value
        val negPrompt = binding.editTextNegPrompt.text?.toString() ?: ""

        val params = ImageGenerationParams(
            prompt = prompt,
            negativePrompt = negPrompt,
            steps = steps,
            cfgScale = cfgScale
        )

        viewLifecycleOwner.lifecycleScope.launch {
            setGenerating(true)
            binding.textViewGeneratingInfo.text = "Generando imagen… ($steps pasos)"

            val result = sdEngine.generateImage(params)

            result.onSuccess { bitmap ->
                generatedBitmap = bitmap
                binding.imageViewResult.setImageBitmap(bitmap)
                binding.imageViewResult.visibility = View.VISIBLE
                binding.buttonSaveImage.visibility = View.VISIBLE
                binding.cardResult.visibility = View.VISIBLE

                // Guardar referencia en base de datos
                saveToDatabase(prompt, negPrompt, steps, cfgScale)
                Toast.makeText(requireContext(), "¡Imagen generada! ✓", Toast.LENGTH_SHORT).show()

            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.imageViewResult.visibility = View.GONE
            }

            setGenerating(false)
        }
    }

    private fun saveImage() {
        val bitmap = generatedBitmap ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val file = File(JarvisConfig.GENERATED_IMAGES_DIR, "img_saved_$timestamp.png")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(),
                    "Imagen guardada en /JarvisApp/generated_images/", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveToDatabase(
        prompt: String, negPrompt: String, steps: Int, cfgScale: Float
    ) {
        val timestamp = System.currentTimeMillis()
        val path = "${JarvisConfig.GENERATED_IMAGES_DIR}/img_$timestamp.png"
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getInstance(requireContext()).generatedImageDao().insert(
                GeneratedImageEntity(
                    prompt = prompt,
                    negativePrompt = negPrompt,
                    imagePath = path,
                    steps = steps,
                    cfgScale = cfgScale
                )
            )
        }
    }

    private fun setGenerating(generating: Boolean) {
        binding.buttonGenerate.isEnabled = !generating
        binding.progressGenerating.visibility = if (generating) View.VISIBLE else View.GONE
        binding.textViewGeneratingInfo.visibility = if (generating) View.VISIBLE else View.GONE
        binding.editTextPrompt.isEnabled = !generating
        binding.sliderSteps.isEnabled = !generating
        binding.sliderCfg.isEnabled = !generating
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
