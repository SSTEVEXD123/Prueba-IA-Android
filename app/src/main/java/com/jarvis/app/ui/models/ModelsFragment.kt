package com.jarvis.app.ui.models

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.jarvis.app.data.database.AiModelEntity
import com.jarvis.app.databinding.FragmentModelsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ModelsFragment
 * Pantalla de gestión de modelos: LLM / Imagen / Visión
 * Permite descargar, activar, eliminar e importar modelos .gguf
 */
class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ModelManagerViewModel by viewModels()
    private lateinit var modelAdapter: ModelAdapter
    private var currentFilter = "llm"

    companion object {
        private const val PICK_FILE_REQUEST = 200
        fun newInstance() = ModelsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRecyclerView()
        setupImportButton()
        observeModels()
        observeDownloads()
    }

    // ─── Tabs: LLM / Imagen / Visión ───

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFilter = when (tab.position) {
                    0 -> "llm"
                    1 -> "image"
                    2 -> "vision"
                    else -> "llm"
                }
                filterModels()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // ─── RecyclerView ───

    private fun setupRecyclerView() {
        modelAdapter = ModelAdapter(
            onDownload = { model -> confirmDownload(model) },
            onActivate = { model -> viewModel.setActiveModel(model.modelId, model.type) },
            onDelete   = { model -> confirmDelete(model) },
            getDownloadState = { modelId -> viewModel.downloadStates.value[modelId] }
        )

        binding.recyclerViewModels.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = modelAdapter
        }
    }

    private fun setupImportButton() {
        binding.fabImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
            }
            startActivityForResult(intent, PICK_FILE_REQUEST)
        }
    }

    // ─── Observar modelos desde Room ───

    private fun observeModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allModels.collectLatest { models ->
                filterAndShow(models)
            }
        }
    }

    private fun observeDownloads() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.downloadStates.collectLatest {
                modelAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun filterModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allModels.collectLatest { models -> filterAndShow(models) }
        }
    }

    private fun filterAndShow(models: List<AiModelEntity>) {
        val filtered = models.filter { it.type == currentFilter }
        modelAdapter.submitList(filtered)
        binding.textViewEmpty.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // ─── Diálogos de confirmación ───

    private fun confirmDownload(model: AiModelEntity) {
        val sizeMb = model.fileSize / (1024 * 1024)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Descargar ${model.displayName}")
            .setMessage(
                "Tamaño aproximado: ${sizeMb} MB\n\n" +
                "${model.description}\n\n" +
                "⚠️ Se recomienda conectarse a Wi-Fi antes de descargar."
            )
            .setPositiveButton("Descargar") { _, _ ->
                viewModel.downloadModel(model)
                Toast.makeText(requireContext(),
                    "Descarga iniciada para ${model.displayName}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDelete(model: AiModelEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar ${model.displayName}")
            .setMessage("¿Eliminar el archivo del modelo? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteModel(model)
                Toast.makeText(requireContext(), "Modelo eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─── Importar modelo desde almacenamiento ───

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "modelo_custom.gguf"

            // Obtener ruta real del URI
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return
            val destFile = java.io.File(
                com.jarvis.app.utils.JarvisConfig.LLM_MODELS_DIR, fileName
            )
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { out -> inputStream.copyTo(out) }

            viewModel.importCustomModel(destFile.absolutePath, fileName.removeSuffix(".gguf"), currentFilter)
            Toast.makeText(requireContext(), "Modelo importado: $fileName", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
